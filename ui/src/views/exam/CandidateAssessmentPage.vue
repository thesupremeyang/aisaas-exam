<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getCandidateSubmission } from '@/api/exam'
import { RouteNames } from '@/router/constants'

const props = defineProps<{ submissionId: string }>()
const router = useRouter()

/**
 * AI 智评已屏蔽（assessment.initial-assessment.enabled=false）：
 * 提交后不再执行真实评测任务，本页只承担提交完成提示：
 * 刚提交 → 先展示「AI 智评正在进行」过场，随后显示「考试完毕」；
 * 再次进入 → 直接显示「考试完毕」；未提交 → 提示先去提交。
 */
type Phase = 'checking' | 'showcase' | 'done' | 'not-submitted'
const phase = ref<Phase>('checking')
const submittedStatus = ref('')

/** 由系统超时收卷的三种状态（含内容不完整与未作答） */
const isTimeoutSubmitted = computed(() =>
  ['TIMEOUT_SUBMITTED', 'PARTIAL_TIMEOUT_SUBMITTED', 'NO_ANSWER'].includes(submittedStatus.value),
)

/** 过场展示时长（毫秒） */
const SHOWCASE_MS = 3200
let showcaseTimer: number | undefined

async function load() {
  try {
    const response = await getCandidateSubmission(props.submissionId)
    const submission = response.data.data || null
    submittedStatus.value = submission?.status || ''
    if (submission && submission.status && submission.status !== 'DRAFT') {
      const justSubmitted = sessionStorage.getItem('exam:just-submitted') === String(props.submissionId)
      sessionStorage.removeItem('exam:just-submitted')
      if (justSubmitted) {
        phase.value = 'showcase'
        showcaseTimer = window.setTimeout(() => {
          phase.value = 'done'
        }, SHOWCASE_MS)
      } else {
        phase.value = 'done'
      }
    } else {
      phase.value = 'not-submitted'
    }
  } catch {
    phase.value = 'not-submitted'
  }
}

function backToPortal() {
  void router.push({ name: RouteNames.EXAM_PORTAL })
}

onMounted(load)
onBeforeUnmount(() => {
  if (showcaseTimer) window.clearTimeout(showcaseTimer)
})
</script>

<template>
  <main class="assessment-page">
    <section class="title-row">
      <div>
        <p class="eyebrow">评审状态</p>
        <h1>作品提交结果</h1>
        <p class="lead">您的作品已提交，评审工作将按统一标准进行；最终成绩以评委发布为准。</p>
      </div>
      <span class="badge" :class="{ processing: phase === 'showcase' }">
        {{ phase === 'showcase' ? 'AI 智评正在进行' : phase === 'done' ? '已提交' : '待提交' }}
      </span>
    </section>

    <!-- 提交成功过场：AI 智评正在进行 -->
    <section v-if="phase === 'showcase'" class="assessment-loading-card" aria-live="polite">
      <div class="loading-topline">
        <div class="loading-status">
          <span class="live-dot" aria-hidden="true" />
          <span>AI 智评正在进行</span>
        </div>
        <span class="loading-refresh">请稍候</span>
      </div>

      <div class="loading-body">
        <div class="assessment-visual" aria-hidden="true">
          <div class="visual-grid" />
          <div class="visual-ring visual-ring-outer" />
          <div class="visual-ring visual-ring-inner" />
          <div class="visual-scan" />
          <div class="visual-core">
            <svg viewBox="0 0 64 64" fill="none">
              <path d="M32 8 49 15v15c0 11-7 20-17 26-10-6-17-15-17-26V15L32 8Z" />
              <path d="m23 32 6 6 12-14" />
            </svg>
          </div>
          <span class="visual-spark visual-spark-one" />
          <span class="visual-spark visual-spark-two" />
          <span class="visual-spark visual-spark-three" />
        </div>

        <div class="loading-copy">
          <h2>AI 智评正在进行</h2>
          <p class="showcase-lead">
            系统正在按照本批次统一的评分标准核验您提交的作品材料与智能体配置摘要，
            评审完成后将由评委复核确认成绩。
          </p>
        </div>
      </div>

      <div class="loading-progress">
        <div class="progress-heading">
          <span>评审进度</span>
        </div>
        <div class="progress-track" role="progressbar" aria-label="AI 智评进度">
          <span class="progress-fill showcase-fill" />
          <span class="progress-sheen" aria-hidden="true" />
        </div>
        <div class="progress-foot">
          <span>结果将自动保存</span>
          <span>最终成绩由评委复核确认</span>
        </div>
      </div>
    </section>

    <!-- 考试完毕 -->
    <section v-else-if="phase === 'done'" class="done-card">
      <div class="done-icon" aria-hidden="true">
        <svg viewBox="0 0 64 64" fill="none">
          <circle cx="32" cy="32" r="30" />
          <path d="m20 33 8 8 16-18" />
        </svg>
      </div>
      <h2>考试完毕，祝您取得优异的成绩</h2>
      <p>
        您的作品<template v-if="isTimeoutSubmitted">（含系统自动交卷）</template>已提交成功，
        最终成绩将由评委评定，成绩发布后可在考试门户查询。
      </p>
      <div class="done-actions">
        <a-button type="primary" size="large" @click="backToPortal">返回考试门户</a-button>
      </div>
    </section>

    <!-- 未提交 -->
    <section v-else-if="phase === 'not-submitted'" class="generating-notice not-submitted">
      <h2>尚未提交作品材料</h2>
      <p>当前考试尚未提交任何作品材料。请先在「我的作品」页面完成作品提交后再查看评审状态。</p>
    </section>

    <!-- 状态确认中 -->
    <section v-else class="generating-notice">
      <p>正在确认提交状态…</p>
    </section>
  </main>
</template>

<style scoped lang="scss">
.assessment-page{width:min(1160px,calc(100% - 36px));margin:auto;padding:34px 0 66px;color:#14233d}.crumb{font-size:12px;color:#8090a8;margin-bottom:20px}.title-row{display:flex;justify-content:space-between;gap:25px;align-items:start}.eyebrow{font-size:11px;letter-spacing:.13em;font-weight:700;color:#4b84d9;margin:0 0 10px}.title-row h1{font:30px/1.3 Georgia,"Microsoft YaHei",serif;margin:0 0 9px}.lead{max-width:700px;color:#6d7c94;line-height:1.75;margin:0}.badge{padding:7px 12px;border-radius:20px;background:#eaf3ff;color:#2d68b4;font-size:12px;white-space:nowrap}.badge.processing{background:#fff4d9;color:#9a6500}.thinking-card,.evidence-card,.advice-card,.generating-notice{background:#fff;border:1px solid #dbe6f6;border-radius:13px;padding:20px 22px;margin-top:18px}.thinking-card{background:#edf6ff;color:#3465a0;border-color:#d5e8ff}.thinking-head{display:flex;justify-content:space-between}.thinking-card ul{list-style:none;padding:0;margin:0}.thinking-card li{padding:5px 0;color:#7794bb}.thinking-card li:before{content:'○';margin-right:8px}.thinking-card li.done{color:#3465a0}.thinking-card li.done:before{content:'✓'}.generating-notice{text-align:center;padding:56px 24px;background:#f7fbff}.generating-notice h2{margin:0 0 10px;color:#174f9d}.generating-notice p{margin:0;color:#6d7c94}
.generating-notice.not-submitted{background:#fff8f0}.generating-notice.not-submitted h2{color:#9a6500}
.generating-notice.failed{background:#fff1f0}.generating-notice.failed h2{color:#cf1322}.result-card{display:grid;grid-template-columns:1fr;gap:18px;background:linear-gradient(135deg,#0a2861,#126be8);color:#fff;border-radius:14px;padding:27px;margin-top:18px}.score-panel small{color:#b9dcff}.score-panel strong{display:block;font-size:48px;margin:7px 0}.score-panel p{color:#d8ebff;line-height:1.75}.conclusion-title{font-size:20px;font-weight:700;margin-bottom:12px}.conclusion-title span{display:inline-flex;background:#dff5e9;color:#12603b;border-radius:20px;padding:5px 10px;font-size:12px;margin-left:7px}.conclusion-title span.rejected{background:#fde8e8;color:#b3261e}.score-grid{display:grid;grid-template-columns:repeat(5,1fr);gap:11px;margin-top:17px}.score-grid article{background:#fff;border:1px solid #dbe6f6;border-radius:12px;padding:15px}.score-grid b{float:right;font-size:22px}.score-grid small{font-size:12px;color:#8796aa}.bar{height:5px;background:#e6edf7;border-radius:3px;margin-top:16px}.bar i{display:block;height:100%;background:linear-gradient(90deg,#1681ff,#6fc1ff);border-radius:inherit}.score-grid p{font-size:11px;line-height:1.6;color:#6d7c94;clear:both}.evidence-card{margin-top:18px}.section-title-blue{color:#2d68b4;font-size:18px;margin:0 0 14px;font-weight:700}.evidence-card h2,.advice-card h2{margin:0;font-size:18px;color:#2d68b4}.evidence-list>div{padding:12px 0;border-bottom:1px solid #edf2f8}.evidence-list span{display:block;color:#6d7c94;font-size:12px;line-height:1.7;margin-top:4px}.advice-card p{color:#6d7c94;line-height:1.8}@media(max-width:720px){.assessment-page{width:calc(100% - 28px)}.title-row,.result-card{grid-template-columns:1fr;display:grid}.score-grid{grid-template-columns:1fr 1fr}}
.failed-actions{display:flex;justify-content:center;gap:12px;margin-top:22px}
.advice-content{display:grid;grid-template-columns:1fr 1fr;gap:20px;margin-top:16px}.advice-content b{color:#174f9d}.advice-content ul{margin:8px 0 0;padding-left:18px;color:#6d7c94;line-height:1.8}

.assessment-loading-card {
  position: relative;
  overflow: hidden;
  margin-top: 18px;
  padding: 28px 30px 24px;
  border: 1px solid #cfe2fb;
  border-radius: 20px;
  background: radial-gradient(circle at 11% 0%, #edf7ff 0, #f7fbff 33%, #fff 82%);
  box-shadow: 0 22px 55px #1b5ea812;
  color: #17345f;
}

.assessment-loading-card::before {
  position: absolute;
  inset: 0;
  pointer-events: none;
  content: '';
  background: linear-gradient(120deg, transparent 28%, #fff8 50%, transparent 72%);
  opacity: 0.42;
}

.loading-topline,
.loading-body,
.loading-progress,
.loading-principles {
  position: relative;
  z-index: 1;
}

.loading-topline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 18px;
  border-bottom: 1px solid #dceafb;
}

.loading-status {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #2367b8;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.live-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #2b7de9;
  box-shadow: 0 0 0 5px #2b7de91c;
  animation: loading-pulse 1.8s ease-in-out infinite;
}

.loading-refresh {
  color: #7b96b5;
  font-size: 12px;
}

.loading-body {
  display: grid;
  grid-template-columns: 250px 1fr;
  align-items: center;
  gap: 28px;
  min-height: 265px;
  padding: 22px 0 18px;
}

.assessment-visual {
  position: relative;
  display: grid;
  width: 218px;
  height: 218px;
  margin: auto;
  place-items: center;
}

.visual-grid {
  position: absolute;
  inset: 23px;
  border: 1px dashed #8ec4f45c;
  border-radius: 50%;
  background: repeating-linear-gradient(0deg, transparent 0 17px, #83b8ee1c 18px 19px),
    repeating-linear-gradient(90deg, transparent 0 17px, #83b8ee1c 18px 19px);
  mask-image: radial-gradient(circle, #000 0 54%, transparent 74%);
  opacity: 0.7;
}

.visual-ring {
  position: absolute;
  border: 1px solid #73b2f569;
  border-radius: 50%;
}

.visual-ring::after {
  position: absolute;
  top: 50%;
  right: -4px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #2b7de9;
  box-shadow: 0 0 0 5px #2b7de91e, 0 0 18px #2b7de9;
  content: '';
  transform: translateY(-50%);
}

.visual-ring-outer {
  inset: 8px;
  border-bottom-color: #2b7de955;
  border-left-color: #2b7de9;
  animation: visual-spin 14s linear infinite;
}

.visual-ring-inner {
  inset: 35px;
  border-right-color: #6caff0;
  border-top-color: #b5d8fa;
  animation: visual-spin-reverse 8s linear infinite;
}

.visual-scan {
  position: absolute;
  inset: 19px;
  border-top: 2px solid #2b7de9a8;
  border-radius: 50%;
  animation: visual-scan 2.8s cubic-bezier(0.77, 0, 0.175, 1) infinite;
}

.visual-core {
  display: grid;
  width: 104px;
  height: 104px;
  border: 1px solid #fff;
  border-radius: 31px;
  background: linear-gradient(145deg, #fff, #e3f1ff);
  box-shadow: 0 14px 35px #2775cf2b, 0 0 0 10px #d8ebfc80;
  place-items: center;
  animation: visual-float 3s cubic-bezier(0.77, 0, 0.175, 1) infinite;
}

.visual-core svg {
  width: 55px;
  height: 55px;
  filter: drop-shadow(0 5px 8px #2b7de936);
}

.visual-core path:first-child {
  fill: #d7ebff;
  stroke: #2b7de9;
  stroke-width: 2;
}

.visual-core path:last-child {
  stroke: #1d66bb;
  stroke-width: 3.5;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.visual-spark {
  position: absolute;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #63b1f2;
  box-shadow: 0 0 0 4px #63b1f21c, 0 0 12px #63b1f2;
  animation: loading-pulse 2.2s ease-in-out infinite;
}

.visual-spark-one {
  top: 36px;
  left: 37px;
}

.visual-spark-two {
  right: 27px;
  bottom: 45px;
  width: 4px;
  height: 4px;
  animation-delay: -0.7s;
}

.visual-spark-three {
  top: 54px;
  right: 13px;
  width: 3px;
  height: 3px;
  animation-delay: -1.35s;
}

.loading-copy {
  max-width: 620px;
}

.loading-copy h2 {
  margin: 0 0 11px;
  color: #174f9d;
  font: 700 30px/1.25 Georgia, 'Microsoft YaHei', serif;
}

.current-check {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  max-width: 430px;
  margin-top: 20px;
  padding: 12px 15px;
  border: 1px solid #d5e7fa;
  border-radius: 13px;
  background: #ffffffb8;
  box-shadow: 0 8px 24px #2d73bd0c;
}

.current-check-index {
  display: grid;
  width: 34px;
  height: 34px;
  flex: none;
  border-radius: 10px;
  background: #e6f2ff;
  color: #2a72c5;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.04em;
  place-items: center;
}

.current-check-copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 2px;
}

.current-check-copy small {
  color: #8aa2bd;
  font-size: 10px;
}

.current-check-copy strong {
  overflow: hidden;
  color: #235d9f;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.current-check-copy span {
  overflow: hidden;
  color: #7187a1;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.current-check-pulse {
  width: 8px;
  height: 8px;
  flex: none;
  border-radius: 50%;
  background: #2b7de9;
  box-shadow: 0 0 0 5px #2b7de91b;
  animation: loading-pulse 1.6s ease-in-out infinite;
}

.loading-progress {
  padding: 0 2px;
}

.progress-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 9px;
  color: #4f7198;
  font-size: 12px;
  font-weight: 700;
}

.progress-heading strong {
  color: #1c66b4;
  font-size: 14px;
}

.progress-track {
  position: relative;
  overflow: hidden;
  height: 10px;
  border-radius: 99px;
  background: #dfedfb;
  box-shadow: inset 0 1px 2px #255e9b14;
}

.progress-fill {
  position: absolute;
  inset: 0;
  display: block;
  border-radius: inherit;
  background: linear-gradient(90deg, #2b7de9, #64b5f3);
  box-shadow: 0 2px 9px #2b7de94a;
  transform-origin: left center;
  transition: transform 220ms cubic-bezier(0.23, 1, 0.32, 1);
}

.progress-sheen {
  position: absolute;
  top: 0;
  bottom: 0;
  left: -35%;
  width: 35%;
  border-radius: inherit;
  background: linear-gradient(90deg, transparent, #fff9, transparent);
  animation: progress-sheen 1.8s linear infinite;
}

.progress-foot {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-top: 9px;
  color: #8399b2;
  font-size: 11px;
}

.loading-principles {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-top: 22px;
  padding-top: 18px;
  border-top: 1px solid #dceafb;
}

.principle-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 0;
}

.principle-index {
  display: grid;
  width: 25px;
  height: 25px;
  flex: none;
  border: 1px solid #c6def5;
  border-radius: 8px;
  color: #4383c2;
  font-size: 10px;
  font-weight: 800;
  place-items: center;
}

.principle-item b,
.principle-item small {
  display: block;
}

.principle-item b {
  margin-bottom: 3px;
  color: #376c9f;
  font-size: 12px;
}

.principle-item small {
  overflow: hidden;
  color: #8196ad;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@keyframes visual-spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes visual-spin-reverse {
  to {
    transform: rotate(-360deg);
  }
}

@keyframes visual-scan {
  0% {
    opacity: 0;
    transform: rotate(-35deg);
  }

  18% {
    opacity: 1;
  }

  82% {
    opacity: 1;
  }

  100% {
    opacity: 0;
    transform: rotate(325deg);
  }
}

@keyframes visual-float {
  0%,
  100% {
    transform: translateY(0);
  }

  50% {
    transform: translateY(-5px);
  }
}

@keyframes loading-pulse {
  0%,
  100% {
    opacity: 0.42;
    transform: scale(0.88);
  }

  50% {
    opacity: 1;
    transform: scale(1);
  }
}

@keyframes progress-sheen {
  to {
    transform: translateX(385%);
  }
}

@media (max-width: 720px) {
  .assessment-loading-card {
    padding: 22px 18px 20px;
  }

  .loading-body {
    grid-template-columns: 1fr;
    gap: 4px;
    min-height: 0;
    padding: 20px 0 22px;
  }

  .assessment-visual {
    width: 184px;
    height: 184px;
    transform: scale(0.88);
  }

  .loading-copy {
    text-align: center;
  }

  .loading-copy h2 {
    font-size: 25px;
  }

  .current-check {
    margin: 18px auto 0;
    text-align: left;
  }

  .loading-principles {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .principle-item small {
    white-space: normal;
  }

  .progress-foot {
    flex-direction: column;
    gap: 4px;
  }

  .loading-refresh {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .assessment-loading-card *,
  .assessment-loading-card *::before,
  .assessment-loading-card *::after {
    animation: none !important;
    transition: none !important;
  }

  .visual-scan {
    opacity: 0.6;
    transform: rotate(90deg);
  }
}

/* 提交完成流程（AI 智评屏蔽后的过场与完成卡） */
.showcase-lead {
  margin: 0;
  color: #6d7c94;
  line-height: 1.8;
}

.showcase-fill {
  animation: showcase-progress 3s cubic-bezier(0.25, 0.6, 0.35, 1) forwards;
}

@keyframes showcase-progress {
  from {
    transform: scaleX(0.08);
  }

  to {
    transform: scaleX(0.92);
  }
}

.done-card {
  margin-top: 18px;
  padding: 56px 24px;
  text-align: center;
  background: linear-gradient(160deg, #f2f9ff 0%, #ffffff 70%);
  border: 1px solid #d5e7fa;
  border-radius: 20px;
  box-shadow: 0 22px 55px #1b5ea812;
}

.done-icon {
  width: 84px;
  height: 84px;
  margin: 0 auto 18px;
}

.done-icon svg {
  width: 100%;
  height: 100%;
}

.done-icon circle {
  fill: #e8f4ff;
  stroke: #2b7de9;
  stroke-width: 2.5;
}

.done-icon path {
  stroke: #1d66bb;
  stroke-width: 5;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.done-card h2 {
  margin: 0 0 12px;
  color: #174f9d;
  font: 700 26px/1.35 Georgia, 'Microsoft YaHei', serif;
}

.done-card p {
  max-width: 560px;
  margin: 0 auto;
  color: #6d7c94;
  line-height: 1.8;
}

.done-actions {
  margin-top: 26px;
}
</style>
