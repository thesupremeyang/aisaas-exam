<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterView } from 'vue-router'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import { useAccountStore } from '@/stores'
import { authApi } from '@/api'
import { isCommunicationRoute } from '@/utils/auth'
import {
  useWebSocketSender
} from '@/websocket/composables/useWebSocketEvent';
import { useWebSocket } from "@/websocket/useWebSocket.ts";

// WebSocket 在应用启动时全局建连；gaveUp 为 true 表示重连已收敛停止，需用户手动重试
const { initWS, retryWS, gaveUp } = useWebSocket();
useWebSocketSender();
initWS();

const wsRetrying = ref(false)
async function handleWsRetry() {
  wsRetrying.value = true
  try {
    await retryWS()
  } finally {
    wsRetrying.value = false
  }
}

function reloadPage() {
  window.location.reload()
}

const accountStore = useAccountStore()
const PRESENCE_HEARTBEAT_INTERVAL = 15_000
let presenceTimer: number | undefined
let stopPresenceWatch: (() => void) | undefined

function stopPresenceHeartbeat() {
  if (presenceTimer !== undefined) {
    window.clearInterval(presenceTimer)
    presenceTimer = undefined
  }
}

function sendPresenceHeartbeat() {
  if (isCommunicationRoute()) return
  if (!accountStore.isLoggedIn) {
    stopPresenceHeartbeat()
    return
  }
  void authApi.heartbeat().catch(() => undefined)
}

function startPresenceHeartbeat() {
  stopPresenceHeartbeat()
  if (!accountStore.isLoggedIn) return
  sendPresenceHeartbeat()
  presenceTimer = window.setInterval(sendPresenceHeartbeat, PRESENCE_HEARTBEAT_INTERVAL)
}

onMounted(() => {
  startPresenceHeartbeat()
  stopPresenceWatch = watch(
    () => accountStore.isLoggedIn,
    (loggedIn) => {
      if (loggedIn) startPresenceHeartbeat()
      else stopPresenceHeartbeat()
    },
  )
})

onBeforeUnmount(() => {
  stopPresenceHeartbeat()
  stopPresenceWatch?.()
})
</script>

<template>
  <AConfigProvider
    :locale="zhCN"
    :theme="{
      token: {
        fontFamily: 'var(--font-family-base)',
      }
     }">
    <RouterView />

    <!-- 重连已停止：明确告知用户，避免页面“静默假死” -->
    <div v-if="gaveUp" class="ws-gave-up">
      <span class="ws-gave-up__text">
        实时连接已断开，自动重连已停止。已填写的内容不受影响，但实时消息可能延迟。
      </span>
      <a-button size="small" type="primary" :loading="wsRetrying" @click="handleWsRetry">
        重新连接
      </a-button>
      <a-button size="small" @click="reloadPage">刷新页面</a-button>
    </div>
  </AConfigProvider>
</template>

<style scoped>
.ws-gave-up {
  position: fixed;
  left: 50%;
  bottom: 16px;
  transform: translateX(-50%);
  z-index: 2000;
  display: flex;
  align-items: center;
  gap: 12px;
  max-width: min(92vw, 720px);
  padding: 10px 14px;
  border-radius: 8px;
  border: 1px solid #ffa39e;
  background: #fff2f0;
  color: #a8071a;
  font-size: 13px;
  line-height: 1.5;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
}
.ws-gave-up__text {
  flex: 1 1 auto;
}
</style>
