export const WS_CONFIG = {
  URL: (() => {
    const isHttps = window.location.protocol === 'https:';
    const protocol = isHttps ? 'wss:' : 'ws:';
    const base = ((import.meta.env.VITE_APP_BASE_API as string) || '').replace(/\/$/, '');
    return `${protocol}//${window.location.hostname}${window.location.port ? ':' + window.location.port : ''}${base}/api/ws/apboa`;
  })(),
  /**
   * 重连策略（2026-09-05 线上事故后收敛）
   *
   * 事故回放：Nginx 缺 `/web/api/ws/` 路由，WebSocket 永远连不上；前端按 1440 次上限
   * 每 800ms~30s 重试一次，几十个考生同时重连，既把页面拖死（考生填的内容走不到
   * autosave，最终留下 80 份空卷），又把服务端日志灌到 53MB / 37 万行。
   *
   * 改为三段式：
   *   1) 快速重试：前 FAST_RECONNECT_ATTEMPTS 次，800ms 起步指数退避，吸收抖动/短暂断网
   *   2) 慢速保活：之后按 SLOW_RECONNECT_DELAY 固定间隔重试
   *   3) 彻底放弃：达到次数上限或总窗口上限即停止，emit WEBSOCKET:GIVE_UP 提示用户
   * 退避统一加抖动，避免机房内几十个客户端在同一刻集体重连（惊群）。
   */
  FAST_RECONNECT_ATTEMPTS: 6,
  INITIAL_RECONNECT_DELAY: 800,
  MAX_RECONNECT_DELAY: 30000,
  SLOW_RECONNECT_DELAY: 15000,
  MAX_RECONNECT_ATTEMPTS: 40,
  RECONNECT_WINDOW_MS: 10 * 60 * 1000,
  RECONNECT_JITTER: 0.3,
  // 客户端主动心跳：及时发现半开连接并重连
  PING_INTERVAL: 20000,
  PONG_TIMEOUT: 8000,
};

export const WS_MESSAGE_TYPES = {
  PING: 'PING',
  PONG: 'PONG',

  CLIENT: 'CLIENT',
  ACCOUNT_ROLE_CHANGE: 'ACCOUNT_ROLE_CHANGE',
  WORKSPACE_FILE_CHANGE: 'WORKSPACE_FILE_CHANGE',
} as const;

export type WSMessageType = typeof WS_MESSAGE_TYPES[keyof typeof WS_MESSAGE_TYPES];
