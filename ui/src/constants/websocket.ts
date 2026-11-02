export const WS_CONFIG = {
  URL: (() => {
    const isHttps = window.location.protocol === 'https:'
    const protocol = isHttps ? 'wss:' : 'ws:'
    const base = ((import.meta.env.VITE_APP_BASE_API as string) || '').replace(/\/$/, '')
    return `${protocol}//${window.location.hostname}${window.location.port ? ':' + window.location.port : ''}${base}/api/ws/apboa`
  })(),
  // ⚠️ 本文件是历史遗留副本，真正生效的是 src/websocket/const/websocket.ts。
  // 这里保持与之一致（9/5 事故后收敛为 40 次 / 10 分钟），避免两处参数打架。
  MAX_RECONNECT_ATTEMPTS: 40,
  INITIAL_RECONNECT_DELAY: 800,
  MAX_RECONNECT_DELAY: 30000,
  SLOW_RECONNECT_DELAY: 15000,
  RECONNECT_WINDOW_MS: 10 * 60 * 1000,
  RECONNECT_JITTER: 0.3,
  PING_INTERVAL: 20000,
  PONG_TIMEOUT: 8000,
};

export const WS_MESSAGE_TYPES = {
  CLIENT: 'CLIENT',
  ACCOUNT_ROLE_CHANGE: 'ACCOUNT_ROLE_CHANGE',
  PING: 'PING',
  PONG: 'PONG',
} as const;

export type WSMessageType = typeof WS_MESSAGE_TYPES[keyof typeof WS_MESSAGE_TYPES];
