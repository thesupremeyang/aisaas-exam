# WebSocket 使用手册

## 架构概览

```
useWebSocket          ← 根组件调用，管理连接生命周期
    │
    ├── ConnectionManager    ← 负责建连/断连/状态维护
    ├── ReconnectionManager  ← 自动重连（三段式：快速重试 → 慢速保活 → 放弃并提示）
    └── EnhancedMessageHandler ← 消息解析 + PING/PONG 处理
            │
            └── eventBus.emit(message.type, payload)
                    │
                    ▼
          任意组件通过 useWebSocketEvent 订阅对应消息类型
```

**核心原则：**
- `useWebSocket` 只应在**根组件或 Layout 组件**中调用一次，负责连接管理。
- 子组件通过 `useWebSocketEvent` / `useWebSocketData` / `useWebSocketSender` 收发消息，与连接层完全解耦。

---

## 快速开始

### 第一步：在根组件初始化连接

```vue
<!-- App.vue 或 Layout.vue -->
<script setup lang="ts">
import { onMounted } from 'vue';
import { useWebSocket } from '@/websocket/useWebSocket';

const { initWS, isConnected, isConnecting, error } = useWebSocket();

onMounted(() => {
  initWS();
});
</script>
```

> `useWebSocket` 在组件卸载时会自动调用 `disconnectWS`，无需手动清理。

### 第二步：在任意子组件中订阅消息

```vue
<!-- TodoList.vue -->
<template>
  <div>
    <div v-for="todo in todos" :key="todo.id">
      {{ todo.title }}
      <button @click="deleteTodo(todo.id)">删除</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import {
  useWebSocketEvent,
  useWebSocketSender,
  useWebSocketData
} from '@/websocket/composables/useWebSocketEvent';
import { WS_MESSAGE_TYPES } from '@/websocket/const/websocket';

const todos = ref([]);

// 订阅新增
useWebSocketEvent(WS_MESSAGE_TYPES.ADD_TODO, (todo) => {
  todos.value.push(todo);
});

// 订阅更新
useWebSocketEvent(WS_MESSAGE_TYPES.UPDATE_TODO, (updatedTodo) => {
  const index = todos.value.findIndex(t => t.id === updatedTodo.id);
  if (index !== -1) {
    todos.value[index] = { ...todos.value[index], ...updatedTodo };
  }
});

// 订阅删除
useWebSocketEvent(WS_MESSAGE_TYPES.DELETE_TODO, ({ id }) => {
  todos.value = todos.value.filter(t => t.id !== id);
});

// 发送消息（自动获取连接 handler，无需手动传入）
const { send, isReady } = useWebSocketSender();

const deleteTodo = (id: string) => {
  send(WS_MESSAGE_TYPES.DELETE_TODO, { id });
};

// 响应式数据：消息到来时自动更新 data.value
const { data: latestTodo, lastUpdate } = useWebSocketData(WS_MESSAGE_TYPES.ADD_TODO);
</script>
```

---

## API 参考

### `useWebSocket()`

在根/Layout 组件中调用，管理 WebSocket 连接生命周期。

```ts
const {
  isConnected,   // Ref<boolean> 是否已连接
  isConnecting,  // Ref<boolean> 是否正在连接中
  error,         // Ref<Error | null> 最近一次错误
  messageHandler,// Ref<EnhancedMessageHandler | null>
  gaveUp,        // Ref<boolean> 重连已停止（超过次数/时间窗口），需用户手动重试
  initWS,        // () => Promise<void> 发起连接
  retryWS,       // () => Promise<void> 手动重试（重置重连状态后再连一次）
  disconnectWS,  // () => void 主动断开（不触发重连）
  sendMessage,   // <T>(type, payload?) => boolean 发送消息
} = useWebSocket();
```

> ⚠️ **注意**：不要在多个子组件中重复调用 `useWebSocket()`，任意一个组件卸载都会导致连接断开。

---

### `useWebSocketEvent(type, handler, options?)`

订阅指定消息类型，组件卸载时**自动取消订阅**，不会内存泄漏。

```ts
const { unsubscribe, isActive } = useWebSocketEvent(
  WS_MESSAGE_TYPES.ADD_TODO,
  (todo) => { /* 处理消息 */ },
  {
    // 过滤器：返回 false 则跳过该消息
    filter: (data) => data.id !== '',

    // latest：同一事件循环内多条消息只处理最后一条（防抖）
    latest: true,

    // 依赖项变化时重新订阅（类似 watchEffect 的 deps）
    deps: [someRef],

    // 是否在组件卸载时自动取消订阅，默认 true
    autoOff: true,

    // handler 内抛出异常时的回调
    onError: (err) => console.error(err),
  }
);

// 手动提前取消订阅
unsubscribe();
```

---

### `useWebSocketEventOnce(type, handler)`

只接收**第一条**匹配的消息，触发后自动清理。组件卸载时若尚未触发，也会自动清理。

```ts
useWebSocketEventOnce(WS_MESSAGE_TYPES.USER, (userData) => {
  console.log('收到用户信息（仅一次）:', userData);
});
```

---

### `useWebSocketData(type, initialValue?, options?)`

响应式数据 Hook，消息到来时自动更新 `data.value`。

```ts
const {
  data,        // Ref<T> 最新消息数据
  error,       // Ref<Error | null> 转换函数抛出的错误
  lastUpdate,  // Ref<Date | null> 最后一次更新时间
} = useWebSocketData(
  WS_MESSAGE_TYPES.UPDATE_TODO,
  null,           // 初始值
  {
    // 对原始数据进行转换后再存入 data.value
    transform: (todo) => ({
      ...todo,
      completedAt: todo.completed ? new Date() : null,
    }),
    filter: (data) => !!data.id,
    onError: (err) => console.error(err),
  }
);
```

---

### `useWebSocketSender()`

发送消息 Hook，**自动监听连接事件获取 handler**，无需手动初始化。

```ts
const {
  send,      // <T>(type, payload?) => boolean
  isReady,   // Ref<boolean> handler 是否就绪（即 WebSocket 已连接）
  setHandler // (handler) => void 手动设置 handler（兼容旧用法）
} = useWebSocketSender();

// 发送消息，未连接时返回 false 并打印警告
const success = send(WS_MESSAGE_TYPES.DELETE_TODO, { id: '123' });
```

---

## 内置系统事件

以下事件由 `useWebSocket` 内部发布，可直接通过 `eventBus` 或 `useWebSocketEvent` 订阅：

| 事件类型 | 触发时机 | payload |
|---|---|---|
| `WEBSOCKET:CONNECTED` | 连接建立成功 | `{ timestamp, handler }` |
| `WEBSOCKET:DISCONNECTED` | 连接断开 | `{ timestamp, manual? }` |
| `WEBSOCKET:CONNECTION_STATE` | 连接状态变更 | `WebSocketState` |
| `WEBSOCKET:ERROR` | 发生错误 | `{ type, error }` |

```ts
import { eventBus } from '@/websocket/core/event-bus';

// 直接使用 eventBus 订阅系统事件（在非组件环境中使用）
const unsub = eventBus.on('WEBSOCKET:DISCONNECTED', ({ manual }) => {
  if (!manual) console.warn('WebSocket 意外断开');
});

// 在组件中也可以用 useWebSocketEvent 订阅（自动清理）
useWebSocketEvent('WEBSOCKET:ERROR', ({ type, error }) => {
  console.error(`WebSocket 错误 [${type}]:`, error);
});
```

---

## 扩展消息类型

在 `const/websocket.ts` 中添加新消息类型：

```ts
export const WS_MESSAGE_TYPES = {
  // ... 已有类型
  ORDER_UPDATE: 'ORDER_UPDATE',  // 新增
} as const;
```

在 `types/events.ts` 中注册对应 payload 类型，获得完整 TypeScript 类型提示：

```ts
export interface MessagePayloadMap {
  // ... 已有类型
  [WS_MESSAGE_TYPES.ORDER_UPDATE]: {
    orderId: string;
    status: 'pending' | 'paid' | 'cancelled';
    updatedAt: string;
  };
}
```

之后即可带类型地订阅：

```ts
useWebSocketEvent(WS_MESSAGE_TYPES.ORDER_UPDATE, (order) => {
  // order 类型自动推导为 { orderId, status, updatedAt }
  console.log(order.status);
});
```

---

## 高级用法

### 消息过滤

```ts
// 只处理 admin 角色的用户消息
useWebSocketEvent(WS_MESSAGE_TYPES.USER, (userData) => {
  console.log('管理员登录:', userData.userInfo);
}, {
  filter: (data) => data.userInfo?.role === 'admin',
  onError: (error) => console.error('处理用户消息失败', error),
});
```

### 防抖（只处理最新一条）

```ts
// 高频推送场景：同一事件循环内只处理最后一条
useWebSocketEvent(WS_MESSAGE_TYPES.ORDER_UPDATE, (order) => {
  renderChart(order);
}, { latest: true });
```

### 依赖变化时重新订阅

```ts
const currentUserId = ref('user-1');

// currentUserId 变化时，自动取消旧订阅并重新订阅
useWebSocketEvent(WS_MESSAGE_TYPES.USER, (userData) => {
  if (userData.clientId === currentUserId.value) {
    updateUserInfo(userData);
  }
}, { deps: [currentUserId] });
```

### 手动控制订阅生命周期

```ts
const { unsubscribe, isActive } = useWebSocketEvent(
  WS_MESSAGE_TYPES.ADD_TODO,
  (todo) => handleTodo(todo),
  { autoOff: false } // 关闭自动取消，手动管理
);

// 条件满足时手动停止
if (someCondition) {
  unsubscribe();
}
```

### 连接状态监控

```ts
const { isConnected, isConnecting, error } = useWebSocket();
// 或在子组件中通过事件订阅
useWebSocketEvent('WEBSOCKET:CONNECTION_STATE', (state) => {
  console.log('连接状态:', state.isConnected, '重连次数:', state.reconnectAttempts);
});
```

---

## 重连机制说明

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `FAST_RECONNECT_ATTEMPTS` | 6 次 | 快速重试次数，用于吸收抖动/短暂断网 |
| `INITIAL_RECONNECT_DELAY` | 800 ms | 首次重连等待时间，之后按 1.5 倍指数退避 |
| `MAX_RECONNECT_DELAY` | 30 秒 | 快速阶段退避上限 |
| `SLOW_RECONNECT_DELAY` | 15 秒 | 快速重试用尽后进入慢速保活，固定间隔 |
| `MAX_RECONNECT_ATTEMPTS` | 40 次 | 重连次数硬上限 |
| `RECONNECT_WINDOW_MS` | 10 分钟 | 重连总时长硬上限（自首次失败起算） |
| `RECONNECT_JITTER` | 0.3 | 退避抖动比例，避免大量客户端同时重连造成惊群 |

**三段式策略**：

1. **快速重试**：前 6 次，800ms 起步指数退避（30s 封顶），应对网络抖动。
2. **慢速保活**：之后固定 15s 一次，应对较长故障。
3. **彻底放弃**：达到 40 次或累计 10 分钟仍未连上，停止重连并 `emit('WEBSOCKET:GIVE_UP')`，
   UI 弹出提示条让用户"重新连接"或"刷新页面"。

- 异常断开（网络波动、服务端关闭）→ **自动重连**，按上述三段式策略
- 主动调用 `disconnectWS()` → **不触发重连**
- 重连成功后，重连计数与窗口自动重置
- 页面切回前台（`visibilitychange`）会立即重试一次，不必等当前退避计时器

> **为什么不再无限重连**：2026-09-05 线上考试时 Nginx 缺 `/web/api/ws/` 路由，WebSocket
> 永远连不上，前端按 1440 次上限持续重试，既把页面拖死（考生填的内容走不到 autosave，
> 留下 80 份空卷），又把服务端日志灌到 53MB / 37 万行。持久故障下"静默无限重连"
> 比"明确告诉用户断了"危害更大。
