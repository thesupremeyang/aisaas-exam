<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import { Modal } from 'ant-design-vue'
import { getCandidateProgress, type CandidateProgress } from '@/api/exam'
import { useAccountStore } from '@/stores'

const accountStore = useAccountStore()
const timing = ref<CandidateProgress | null>(null)
const nowMs = ref(Date.now())
const clockOffsetMs = ref(0)

let tickTimer: number | undefined
let pollTimer: number | undefined
let loading = false
let campaignId = ''
let reminderShown = false
let expiredEmitted = false
let lastState: 'WAITING' | 'OPEN' | 'ENDED' | null = null

const isPlatformAdmin = computed(
  () => accountStore.userInfo?.username?.toLowerCase() === 'admin',
)

function parseTime(value?: string): number | null {
  if (!value) return null
  const normalized = value.includes(' ') && !value.includes('T') ? value.replace(' ', 'T') : value
  const parsed = dayjs(normalized).valueOf()
  return Number.isNaN(parsed) ? null : parsed
}

const serverNowMs = computed(() => nowMs.value + clockOffsetMs.value)
const startMs = computed(() => parseTime(timing.value?.examStartAt))
const endMs = computed(() => parseTime(timing.value?.examEndAt))

const state = computed<'WAITING' | 'OPEN' | 'ENDED' | null>(() => {
  if (!timing.value || isPlatformAdmin.value) return null
  if (startMs.value !== null && serverNowMs.value < startMs.value) return 'WAITING'
  if (endMs.value !== null && serverNowMs.value >= endMs.value) return 'ENDED'
  return timing.value.examState || 'OPEN'
})

const remainingSeconds = computed(() => {
  const target = state.value === 'WAITING' ? startMs.value : endMs.value
  if (target === null || target === undefined) return 0
  return Math.max(0, Math.ceil((target - serverNowMs.value) / 1000))
})

const visible = computed(
  () => !isPlatformAdmin.value && !!timing.value && state.value !== null
    && (startMs.value !== null || endMs.value !== null),
)

const urgent = computed(() => state.value === 'OPEN' && remainingSeconds.value <= 30 * 60)

const label = computed(() => {
  if (state.value === 'WAITING') return '考前准备'
  if (state.value === 'OPEN') return '考试剩余'
  return '考试状态'
})

function formatSeconds(seconds: number): string {
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const rest = seconds % 60
  return [hours, minutes, rest].map((value) => String(value).padStart(2, '0')).join(':')
}

function notifyState() {
  if (!campaignId) return
  const currentState = state.value
  if (currentState !== lastState) {
    if (lastState !== null) {
      window.dispatchEvent(new Event('exam:window-state-updated'))
    }
    lastState = currentState
  }
  if (state.value === 'OPEN' && remainingSeconds.value > 0 && remainingSeconds.value <= 30 * 60 && !reminderShown) {
    reminderShown = true
    sessionStorage.setItem(`exam:reminder-shown:${campaignId}`, '1')
    Modal.warning({
      title: '考试时间提醒',
      content: `本次考试还剩 ${formatSeconds(remainingSeconds.value)}，请及时保存并提交作品。`,
      centered: true,
    })
  }
  if (state.value === 'ENDED' && !expiredEmitted) {
    expiredEmitted = true
    window.dispatchEvent(new Event('exam:expired'))
  }
}

async function refresh() {
  const nextCampaignId = sessionStorage.getItem('exam:campaignId') || ''
  if (!nextCampaignId || isPlatformAdmin.value) {
    timing.value = null
    campaignId = ''
    lastState = null
    return
  }
  if (nextCampaignId !== campaignId) {
    campaignId = nextCampaignId
    reminderShown = sessionStorage.getItem(`exam:reminder-shown:${campaignId}`) === '1'
    expiredEmitted = false
    lastState = null
  }
  if (loading) return
  loading = true
  try {
    const response = await getCandidateProgress(campaignId)
    timing.value = response.data.data || null
    const serverTime = parseTime(timing.value?.serverNow)
    if (serverTime !== null) {
      clockOffsetMs.value = serverTime - Date.now()
    }
    notifyState()
  } catch {
    // 倒计时已有本地快照时保留展示，下一次轮询继续校准服务端时间。
  } finally {
    loading = false
  }
}

function tick() {
  nowMs.value = Date.now()
  notifyState()
}

function refreshFromContext() {
  void refresh()
}

onMounted(() => {
  window.addEventListener('exam:context-updated', refreshFromContext)
  tickTimer = window.setInterval(tick, 1000)
  pollTimer = window.setInterval(() => void refresh(), 5000)
  void refresh()
})

onBeforeUnmount(() => {
  window.removeEventListener('exam:context-updated', refreshFromContext)
  if (tickTimer) window.clearInterval(tickTimer)
  if (pollTimer) window.clearInterval(pollTimer)
})
</script>

<template>
  <div
    v-if="visible"
    class="exam-timer-badge"
    :class="{ waiting: state === 'WAITING', urgent, ended: state === 'ENDED' }"
    :title="state === 'WAITING' ? '考试尚未开始' : state === 'OPEN' ? '倒计时以服务器时间为准' : '考试时间已结束'"
    aria-live="polite"
  >
    <span class="exam-timer-dot" aria-hidden="true" />
    <span class="exam-timer-label">{{ label }}</span>
    <strong v-if="state !== 'WAITING'">{{ state === 'ENDED' ? '已结束' : formatSeconds(remainingSeconds) }}</strong>
  </div>
</template>

<style scoped lang="scss">
.exam-timer-badge {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 34px;
  padding: 0 12px;
  border: 1px solid #cfe0ff;
  border-radius: 999px;
  background: #f2f7ff;
  color: #245da9;
  font-size: 12px;
  white-space: nowrap;
}

.exam-timer-badge strong {
  color: #174f9d;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.03em;
}

.exam-timer-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #2f73d9;
  box-shadow: 0 0 0 3px #dceaff;
}

.exam-timer-badge.waiting {
  border-color: #d9d2ff;
  background: #f6f3ff;
  color: #644bc1;
}

.exam-timer-badge.waiting .exam-timer-dot {
  background: #7658dc;
  box-shadow: 0 0 0 3px #e9e3ff;
}

.exam-timer-badge.urgent {
  border-color: #ffd591;
  background: #fff7e6;
  color: #ad6800;
}

.exam-timer-badge.urgent strong {
  color: #d46b08;
}

.exam-timer-badge.urgent .exam-timer-dot {
  background: #fa8c16;
  box-shadow: 0 0 0 3px #ffe7ba;
  animation: exam-timer-pulse 1.4s ease-in-out infinite;
}

.exam-timer-badge.ended {
  border-color: #d9d9d9;
  background: #fafafa;
  color: #8c8c8c;
}

.exam-timer-badge.ended .exam-timer-dot {
  background: #8c8c8c;
  box-shadow: 0 0 0 3px #f0f0f0;
}

@keyframes exam-timer-pulse {
  50% { transform: scale(0.72); opacity: 0.55; }
}

@media (max-width: 640px) {
  .exam-timer-badge {
    gap: 5px;
    padding: 0 9px;
  }

  .exam-timer-label {
    display: none;
  }
}
</style>
