import {WS_CONFIG} from '../const/websocket';
import type {WebSocketState} from '../types/websocket';

// 连接事件回调类型
export interface ConnectionCallbacks {
  /** 连接状态变更 */
  onStateChange?: (state: WebSocketState) => void;
  /** 连接断开（含正常断开和异常断开） */
  onClose?: (event: CloseEvent) => void;
  /** 连接错误（握手阶段的错误） */
  onError?: (event: Event) => void;
  /** 收到消息 */
  onMessage?: (event: MessageEvent) => void;
}

export class ConnectionManager {
  private state: WebSocketState = {
    socket: null,
    reconnectAttempts: 0,
    isConnecting: false,
    isConnected: false,
    reconnectWindowStartedAt: 0,
    gaveUp: false,
  };

  // 是否已主动断开，用于区分主动断开和异常断开
  private manualDisconnect = false;
  private pingTimer?: number;
  private lastActivityAt = 0;

  constructor(private callbacks: ConnectionCallbacks = {}) {}

  getState(): WebSocketState {
    return { ...this.state };
  }

  isConnected(): boolean {
    return this.state.isConnected;
  }

  private updateState(partialState: Partial<WebSocketState>) {
    this.state = { ...this.state, ...partialState };
    this.callbacks.onStateChange?.(this.state);
  }

  async connect(url: string): Promise<WebSocket> {
    if (this.state.isConnecting) {
      throw new Error('WebSocket is already connecting');
    }

    this.manualDisconnect = false;
    this.updateState({ isConnecting: true });

    return new Promise((resolve, reject) => {
      try {
        const socket = new WebSocket(url);

        // 握手成功
        socket.onopen = () => {
          this.updateState({
            socket,
            isConnecting: false,
            isConnected: true,
            reconnectAttempts: 0,
            reconnectWindowStartedAt: 0,
            gaveUp: false,
          });
          this.startHeartbeat(socket);
          resolve(socket);
        };

        // 握手阶段的错误（连接建立前触发）
        // 注意：onerror 之后必然触发 onclose，由 onclose 统一处理重连
        socket.onerror = (event) => {
          if (!this.state.isConnected) {
            // 握手阶段失败，reject promise
            this.updateState({ isConnecting: false, isConnected: false });
            this.callbacks.onError?.(event);
            reject(event);
          } else {
            // 已连接后的错误，通知上层，由 onclose 统一处理后续
            this.callbacks.onError?.(event);
          }
        };

        // 统一在 onclose 处理断开逻辑，避免 onerror+onclose 双重触发重连
        socket.onclose = (event) => {
          const wasConnected = this.state.isConnected;
          this.stopHeartbeat();
          this.updateState({
            socket: null,
            isConnected: false,
            isConnecting: false,
          });
          // 只有连接建立后断开才通知上层（握手失败已通过 onerror+reject 处理）
          if (wasConnected) {
            this.callbacks.onClose?.(event);
          }
        };

        // 消息统一在 ConnectionManager 内分发，避免外部覆盖 onmessage
        socket.onmessage = (event) => {
          this.noteActivity(event);
          this.callbacks.onMessage?.(event);
        };

      } catch (error) {
        this.updateState({ isConnecting: false, isConnected: false });
        reject(error);
      }
    });
  }

  /** 客户端主动心跳 + 静默检测：半开连接（TCP 未关但对端已死）能被及时发现 */
  private startHeartbeat(socket: WebSocket) {
    this.stopHeartbeat();
    this.lastActivityAt = Date.now();

    this.pingTimer = window.setInterval(() => {
      if (socket.readyState !== WebSocket.OPEN) return;
      const silentMs = Date.now() - this.lastActivityAt;
      // 长时间无任何消息（含服务端 PING）才判定半开，避免误杀仅不回 PONG 的后端
      if (silentMs >= WS_CONFIG.PING_INTERVAL + WS_CONFIG.PONG_TIMEOUT) {
        try { socket.close(); } catch { /* ignore */ }
        return;
      }
      try {
        socket.send(JSON.stringify({ type: 'PING', content: 'PING' }));
      } catch {
        try { socket.close(); } catch { /* ignore */ }
      }
    }, WS_CONFIG.PING_INTERVAL);
  }

  private noteActivity(event: MessageEvent) {
    this.lastActivityAt = Date.now();
    void event;
  }

  private stopHeartbeat() {
    if (this.pingTimer !== undefined) {
      clearInterval(this.pingTimer);
      this.pingTimer = undefined;
    }
  }

  disconnect() {
    this.manualDisconnect = true;
    this.stopHeartbeat();
    if (this.state.socket) {
      this.state.socket.close();
      this.updateState({
        socket: null,
        isConnected: false,
        isConnecting: false,
      });
    }
  }

  /** 是否是主动断开（用于上层判断是否需要重连） */
  isManualDisconnect(): boolean {
    return this.manualDisconnect;
  }

  /**
   * 是否还应继续重连。
   * 三重收敛：主动断开不重连、次数不超 MAX_RECONNECT_ATTEMPTS、总时长不超 RECONNECT_WINDOW_MS。
   */
  shouldReconnect(): boolean {
    if (this.manualDisconnect || this.state.gaveUp) return false;
    if (this.state.reconnectAttempts >= WS_CONFIG.MAX_RECONNECT_ATTEMPTS) return false;
    const startedAt = this.state.reconnectWindowStartedAt;
    if (startedAt > 0 && Date.now() - startedAt >= WS_CONFIG.RECONNECT_WINDOW_MS) return false;
    return true;
  }

  /** 开启一轮重连窗口：只在首次失败时记录起点，后续重试沿用同一起点 */
  beginReconnectWindow() {
    if (this.state.reconnectWindowStartedAt === 0) {
      this.updateState({ reconnectWindowStartedAt: Date.now() });
    }
  }

  /** 彻底放弃重连：停止调度，由上层提示用户手动重试或刷新页面 */
  giveUp() {
    if (this.state.gaveUp) return;
    this.updateState({ gaveUp: true });
  }

  hasGaveUp(): boolean {
    return this.state.gaveUp;
  }

  getReconnectDelay(): number {
    const attempt = this.state.reconnectAttempts;
    let delay: number;
    if (attempt < WS_CONFIG.FAST_RECONNECT_ATTEMPTS) {
      // 800ms 起步、1.5 倍退避、30s 封顶：掉线后尽快恢复，避免考试中长时间“假死”
      delay = Math.min(
        WS_CONFIG.INITIAL_RECONNECT_DELAY * Math.pow(1.5, attempt),
        WS_CONFIG.MAX_RECONNECT_DELAY
      );
    } else {
      // 进入慢速保活：持久故障下不再高频重试，避免拖死页面与灌爆服务端日志
      delay = WS_CONFIG.SLOW_RECONNECT_DELAY;
    }
    return applyJitter(delay);
  }

  incrementReconnectAttempts() {
    this.updateState({
      reconnectAttempts: this.state.reconnectAttempts + 1,
    });
  }

  resetReconnectAttempts() {
    this.updateState({ reconnectAttempts: 0 });
  }

  /** 完整重置重连状态（次数 + 窗口 + 放弃标记），用于用户手动重试 */
  resetReconnectState() {
    this.updateState({
      reconnectAttempts: 0,
      reconnectWindowStartedAt: 0,
      gaveUp: false,
    });
  }
}

/**
 * 退避抖动：把重试时刻打散，避免机房内几十个客户端在同一刻集体重连（惊群）。
 * 返回 [delay * (1 - ratio), delay * (1 + ratio)] 区间内的随机值。
 */
function applyJitter(delay: number): number {
  const ratio = WS_CONFIG.RECONNECT_JITTER;
  if (!ratio || ratio <= 0) return Math.round(delay);
  const delta = delay * ratio;
  return Math.round(delay - delta + Math.random() * delta * 2);
}
