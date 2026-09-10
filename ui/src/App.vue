<script setup lang="ts">
import { onBeforeUnmount, onMounted, watch } from 'vue'
import { RouterView } from 'vue-router'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import { useAccountStore } from '@/stores'
import { authApi } from '@/api'
import { isCommunicationRoute } from '@/utils/auth'
import {
  useWebSocketSender
} from '@/websocket/composables/useWebSocketEvent';
import { useWebSocket } from "@/websocket/useWebSocket.ts";

void (() => {
  const { initWS } = useWebSocket();
  useWebSocketSender();
  initWS();
})();

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
  </AConfigProvider>
</template>

<style scoped></style>
