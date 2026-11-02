import type { WSMessageType } from '../const/websocket';

export interface WebSocketMessage {
  type: WSMessageType;
  content?: string;
}

export interface UserInfoMessage {
  clientId: string;
  userInfo: any;
}

export interface WebSocketState {
  socket: WebSocket | null;
  reconnectAttempts: number;
  isConnecting: boolean;
  isConnected: boolean;
  /** 本轮重连窗口起点（首次连接失败时记录，0 表示未处于重连中），用于限制总重连时长 */
  reconnectWindowStartedAt: number;
  /** 是否已彻底放弃重连（需用户手动重试或刷新页面） */
  gaveUp: boolean;
}
