<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { LockOutlined } from '@ant-design/icons-vue'
import type { CandidateProgress, ExamCampaign } from '@/api/exam'

const props = defineProps<{
  campaign: ExamCampaign | null
  progress: CandidateProgress | null
}>()

const emit = defineEmits<{
  'exam-started': []
  'enter-exam': []
}>()

const nowMs = ref(Date.now())
const clockOffsetMs = ref(0)

function parseTime(value?: string): number | null {
  if (!value) return null
  const normalized = value.includes(' ') && !value.includes('T') ? value.replace(' ', 'T') : value
  const parsed = dayjs(normalized).valueOf()
  return Number.isNaN(parsed) ? null : parsed
}

function syncClock(): void {
  const serverTime = parseTime(props.progress?.serverNow)
  clockOffsetMs.value = serverTime === null ? 0 : serverTime - Date.now()
}

const serverNowMs = computed(() => nowMs.value + clockOffsetMs.value)
const startMs = computed(() => parseTime(props.progress?.examStartAt))
const remainingSeconds = computed(() => {
  if (startMs.value === null) return 0
  return Math.max(0, Math.ceil((startMs.value - serverNowMs.value) / 1000))
})
const hasStarted = computed(() => startMs.value !== null && remainingSeconds.value === 0)
const countdown = computed(() => {
  const totalSeconds = remainingSeconds.value
  return {
    hours: Math.floor(totalSeconds / 3600),
    minutes: Math.floor((totalSeconds % 3600) / 60),
    seconds: totalSeconds % 60,
  }
})

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

function tick(): void {
  nowMs.value = Date.now()
}

function enterExam(): void {
  if (!hasStarted.value) return
  emit('enter-exam')
}

watch(() => props.progress?.serverNow, syncClock, { immediate: true })
watch(hasStarted, (started, previous) => {
  if (started && previous === false) emit('exam-started')
})

let tickTimer: number | undefined

onMounted(() => {
  tickTimer = window.setInterval(tick, 1000)
})

onBeforeUnmount(() => {
  if (tickTimer) window.clearInterval(tickTimer)
})
</script>

<template>
  <div class="exam-prestart">
    <main class="prestart-main">
      <section class="countdown-card" aria-labelledby="exam-prestart-title">
        <p class="eyebrow">{{ campaign?.name || '考试批次' }}</p>
        <h1 id="exam-prestart-title">考试时间尚未开始</h1>
        <p class="instruction">请倒计时结束后再点击进入考试</p>

        <div class="countdown-row" aria-label="考试倒计时" aria-live="polite">
          <div class="time-block">
            <span class="time-value">{{ pad(countdown.hours) }}</span>
            <span class="time-label">时</span>
          </div>
          <div class="time-block">
            <span class="time-value">{{ pad(countdown.minutes) }}</span>
            <span class="time-label">分</span>
          </div>
          <div class="time-block">
            <span class="time-value">{{ pad(countdown.seconds) }}</span>
            <span class="time-label">秒</span>
          </div>
        </div>

        <button
          class="entry-button"
          :class="{ ready: hasStarted }"
          type="button"
          :disabled="!hasStarted"
          @click="enterExam"
        >
          <LockOutlined v-if="!hasStarted" aria-hidden="true" />
          进入考试
        </button>
      </section>
    </main>
  </div>
</template>

<style scoped lang="scss">
.exam-prestart {
  display: flex;
  min-height: calc(100vh - 96px);
  box-sizing: border-box;
  align-items: center;
  margin: -32px -20px -56px;
  padding: 48px 20px 72px;
  color: #14213d;
  background: #f5f7fb;
}

.prestart-main {
  width: min(900px, 100%);
  margin: 0 auto;
}

.countdown-card {
  position: relative;
  display: flex;
  min-height: 520px;
  box-sizing: border-box;
  overflow: hidden;
  align-items: center;
  flex-direction: column;
  justify-content: center;
  padding: 64px 72px;
  color: #ffffff;
  text-align: center;
  background: #172b52;
  border-radius: 24px;
  box-shadow: 0 22px 44px rgba(22, 45, 89, 0.2);
}

.countdown-card::before,
.countdown-card::after {
  position: absolute;
  content: '';
  pointer-events: none;
  border: 1px solid rgba(160, 193, 255, 0.12);
  border-radius: 50%;
}

.countdown-card::before {
  width: 560px;
  height: 560px;
  right: -210px;
  bottom: -350px;
}

.countdown-card::after {
  width: 350px;
  height: 350px;
  right: -110px;
  bottom: -220px;
  border-color: rgba(151, 190, 255, 0.09);
}

.eyebrow,
h1,
.instruction,
.countdown-row,
.entry-button {
  position: relative;
  z-index: 1;
}

.eyebrow {
  margin: 0 0 18px;
  color: #b8c9ee;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

h1 {
  margin: 0;
  color: #ffffff;
  font-size: clamp(30px, 4.4vw, 46px);
  line-height: 1.2;
  letter-spacing: -0.035em;
}

.instruction {
  margin: 15px 0 0;
  color: #b8c9ee;
  font-size: 15px;
  line-height: 1.7;
}

.countdown-row {
  display: grid;
  width: 100%;
  max-width: 700px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin: 48px 0 42px;
}

.time-block {
  padding: 24px 16px 20px;
  background: rgba(255, 255, 255, 0.095);
  border: 1px solid rgba(190, 211, 255, 0.13);
  border-radius: 15px;
}

.time-value {
  display: block;
  color: #ffffff;
  font-size: clamp(52px, 9vw, 88px);
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  letter-spacing: -0.06em;
  line-height: 1;
}

.time-label {
  display: block;
  margin-top: 10px;
  color: #9fb3dc;
  font-size: 13px;
}

.entry-button {
  display: inline-flex;
  min-width: 220px;
  min-height: 56px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 0 22px;
  color: #eef4ff;
  background: #5274bb;
  border: 1px solid rgba(191, 215, 255, 0.38);
  border-radius: 12px;
  box-shadow:
    0 8px 18px rgba(10, 26, 61, 0.22),
    inset 0 1px 0 rgba(255, 255, 255, 0.12);
  cursor: not-allowed;
  font-size: 15px;
  font-weight: 650;
  letter-spacing: 0.02em;
}

.entry-button.ready {
  color: #ffffff;
  background: #2d62f4;
  border-color: #2d62f4;
  box-shadow: 0 10px 20px rgba(45, 98, 244, 0.24);
  cursor: pointer;
}

.entry-button.ready:hover {
  background: #1f49c8;
  border-color: #1f49c8;
}

@media (max-width: 620px) {
  .exam-prestart {
    min-height: calc(100vh - 72px);
    margin: -32px -14px -40px;
    padding: 28px 14px 44px;
  }

  .countdown-card {
    min-height: 480px;
    padding: 48px 20px;
    border-radius: 20px;
  }

  .countdown-row {
    gap: 7px;
    margin: 40px 0 34px;
  }

  .time-block {
    padding: 18px 5px 15px;
  }

  .time-value {
    font-size: clamp(36px, 14vw, 58px);
  }

  .entry-button {
    width: 100%;
  }
}
</style>
