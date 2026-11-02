import { ConnectionManager } from './connection-manager';

export class ReconnectionManager {
  private reconnectTimer?: number;

  constructor(
    private connectionManager: ConnectionManager,
    private onReconnect: () => Promise<void>,
    private onGiveUp?: () => void
  ) {}

  /**
   * 调度一次重连。收敛判断全部在这里完成：
   * 主动断开 → 直接返回；次数/总窗口耗尽 → 标记放弃并只通知一次上层。
   */
  scheduleReconnect() {
    if (this.connectionManager.isManualDisconnect()) return;

    this.connectionManager.beginReconnectWindow();

    if (!this.connectionManager.shouldReconnect()) {
      const firstTime = !this.connectionManager.hasGaveUp();
      this.connectionManager.giveUp();
      if (firstTime) {
        console.warn('WebSocket 重连已停止：超过次数或时间窗口上限，请用户手动重试');
        this.onGiveUp?.();
      }
      return;
    }

    const delay = this.connectionManager.getReconnectDelay();

    this.clearReconnectTimer();

    this.reconnectTimer = window.setTimeout(async () => {
      this.connectionManager.incrementReconnectAttempts();

      // 已在连/已连接则跳过本轮，避免双连接
      if (this.connectionManager.isConnected()) return;

      try {
        await this.onReconnect();
      } catch {
        // 失败后由 onClose/onError 再次调度
      }
    }, delay);
  }

  cancelReconnect() {
    this.clearReconnectTimer();
    this.connectionManager.resetReconnectState();
  }

  private clearReconnectTimer() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = undefined;
    }
  }
}
