<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { useExamStore } from '@/stores'
import { decodeAssessmentReport } from './report'
import { currentExamRubric } from './rubric'

const props = defineProps<{ submissionId: string }>()
const examStore = useExamStore()
const polling = ref<number | undefined>()
const retrying = ref(false)

const report = computed(() => decodeAssessmentReport(examStore.report?.reportJson))
const dimensions = computed(() => currentExamRubric().map((item) => ({
  code: item.key, name: item.label, maxScore: item.maximum, comment: item.description,
  score: report.value.dimensionScores[item.key] || 0,
})))
const rubricTotal = computed(() => dimensions.value.reduce((total, item) => total + item.maxScore, 0))
const aiEvidenceItems = [
  {
    title: '作品说明与功能目标',
    content: '统一查看作品目标、适用场景、使用对象及核心功能，确认提交内容与考试要求一致。',
  },
  {
    title: '演示视频与操作截图',
    content: '结合演示过程和关键操作截图核验功能表现，让作品结果有清晰、可复核的展示依据。',
  },
  {
    title: '技术方案与配置摘要',
    content: '检查模型、提示词、知识库、技能与工具等配置，全面了解作品的技术实现与运行逻辑。',
  },
  {
    title: '代码、资源与部署说明',
    content: '关注代码包、依赖资源和部署条件，评估作品的完整性、可追溯性与复现条件。',
  },
  {
    title: '统一标准与多维度复核',
    content: '按照同一评分标准，从场景价值、功能完整度、创新性、技术说明和可验证性等维度综合评估。',
  },
]
const taskState = computed(() => examStore.task?.status || 'QUEUED')
const hasTask = computed(() => !!examStore.task)
const completed = computed(() => taskState.value === 'SUCCEEDED' || !!examStore.report)
const notSubmitted = computed(() => !hasTask.value && !examStore.report)
const failed = computed(() => taskState.value === 'FAILED')
const failureMessage = computed(() => {
  const detail = examStore.task?.errorMessage?.trim() || ''
  if (/timeout|timed out|超时/i.test(detail)) {
    return 'AI 评测服务本次响应超时，已提交的作品内容仍然保留，可以点击下方按钮重新提交并参与评价。'
  }
  return '本次作品提交的 AI 智评未能成功生成，已提交的作品内容仍然保留，可以点击下方按钮重新提交并参与评价。'
})
const assessmentStages = [
  { title: '读取作品材料', description: '确认已提交内容与材料' },
  { title: '对齐统一标准', description: '按本批次 Rubric 逐项核验' },
  { title: '检查评分依据', description: '提取可复核的材料证据' },
  { title: '整理智评结果', description: '生成结构化评测报告' },
]
const progressPercent = computed(() => {
  const progress = Number(examStore.task?.progress)
  if (Number.isFinite(progress)) return Math.min(100, Math.max(0, progress))
  return taskState.value === 'RUNNING' ? 35 : 10
})
const currentStageIndex = computed(() => {
  if (taskState.value === 'QUEUED') return 0
  if (progressPercent.value < 55) return 1
  if (progressPercent.value < 80) return 2
  return 3
})
const currentStage = computed(() => assessmentStages[currentStageIndex.value] ?? {
  title: '读取作品材料',
  description: '确认已提交内容与材料',
})
const badgeText = computed(() => {
  if (notSubmitted.value) return '待提交'
  if (completed.value) return '智评完成'
  if (failed.value) return '智评失败'
  return '结果生成中'
})
const conclusionRejected = computed(() =>
  /不及格|不通过|无效|乱填|不合格|不符合|否决|驳回/.test(report.value.conclusion || ''),
)
const conclusionBadge = computed(() =>
  conclusionRejected.value
    ? { text: '建议不通过 · 进入复核', cls: 'rejected' }
    : { text: '建议通过 · 进入复核', cls: 'passed' },
)

async function load() {
  try {
    await examStore.loadTask(props.submissionId)
    if (completed.value) await examStore.loadReport(props.submissionId)
    else examStore.report = null
    syncPolling()
  } catch {
    message.error('加载 AI 智评记录失败，请稍后重试')
  }
}

function stopPolling() {
  if (polling.value) window.clearInterval(polling.value)
  polling.value = undefined
}

function syncPolling() {
  if (completed.value || notSubmitted.value || failed.value) {
    stopPolling()
    return
  }
  if (!polling.value) polling.value = window.setInterval(load, 2500)
}

function retryAssessment() {
  if (retrying.value) return
  Modal.confirm({
    title: '重新提交作品',
    content: '将使用当前已提交的作品材料重新排队 AI 智评，确认继续吗？',
    okText: '重新提交',
    cancelText: '取消',
    onOk: async () => {
      retrying.value = true
      try {
        await examStore.retryAssessment(props.submissionId)
        message.success('作品已重新提交，正在重新生成 AI 智评')
        await load()
      } catch {
        message.error('重新提交失败，请稍后重试')
      } finally {
        retrying.value = false
      }
    },
  })
}

onMounted(async () => {
  await load()
})
onBeforeUnmount(() => {
  stopPolling()
})
</script>

<template>
  <main class="assessment-page">
    <section class="title-row">
      <div>
        <p class="eyebrow">AI 智评报告</p>
        <h1>智能体作品 AI 智评</h1>
        <p class="lead">基于考生填报信息、受控材料和平台内智能体配置摘要生成；最终成绩以评委发布为准。</p>
      </div>
      <span class="badge" :class="{ processing: !completed }">{{ badgeText }}</span>
    </section>

    <section v-if="completed" class="thinking-card">
      <div class="thinking-head"><b>AI 智评过程</b><span>已完成</span></div>
      <ul>
        <li class="done">解析作品信息、材料与智能体配置摘要</li>
        <li class="done">对齐本批次评分标准 Rubric</li>
        <li class="done">提取可引用评分证据、风险点和改进建议</li>
        <li class="done">生成结构化 AI 智评报告</li>
      </ul>
    </section>

    <template v-if="completed">
      <section class="result-card">
        <div class="score-panel" style="display: none">
          <small>AI 建议总分</small><strong>{{ report.totalScore }}</strong><small>/ {{ rubricTotal }}</small>
          <p>{{ conclusionBadge.text }}</p>
        </div>
        <div class="conclusion-panel">
          <div class="conclusion-title">AI智评建议 <span :class="conclusionBadge.cls">{{ conclusionBadge.text }}</span></div>
        </div>
      </section>

      <section class="score-grid" style="display: none">
        <article v-for="item in dimensions" :key="item.code">
          <span>{{ item.name }}</span><b style="display: none">{{ item.score }}<small>/{{ item.maxScore }}</small></b>
          <div class="bar" style="display: none"><i :style="{ width: `${item.maxScore ? item.score / item.maxScore * 100 : 0}%` }" /></div>
          <p>{{ item.comment }}</p>
        </article>
      </section>

      <section class="evidence-card">
        <div><h2 class="section-title-blue">AI智评依据</h2></div>
        <div class="evidence-list"><div v-for="evidence in aiEvidenceItems" :key="evidence.title"><b>{{ evidence.title }}</b><span>{{ evidence.content }}</span></div></div>
      </section>
      <section class="advice-card" v-if="false">
        <h2 class="section-title-blue">改进意见</h2>
        <div v-if="report.risks.length || report.suggestions.length" class="advice-content">
          <div v-if="report.risks.length"><b>风险点</b><ul><li v-for="item in report.risks" :key="item">{{ item }}</li></ul></div>
          <div v-if="report.suggestions.length"><ul><li v-for="item in report.suggestions" :key="item">{{ item }}</li></ul></div>
        </div>
        <p v-else>本报告为历史版本，未生成风险点与改进建议。</p>
      </section>
    </template>
    <section v-if="notSubmitted" class="generating-notice not-submitted">
      <h2>尚未提交作品材料</h2>
      <p>当前考试尚未提交任何作品材料，暂无可进行 AI 智评的内容。请先在「我的作品」页面完成作品提交后再查看 AI 智评报告。</p>
    </section>
    <section v-else-if="failed" class="generating-notice failed">
      <h2>AI 智评生成失败</h2>
      <p>{{ failureMessage }}</p>
      <div class="failed-actions">
        <a-button type="primary" :loading="retrying" @click="retryAssessment">重新提交</a-button>
      </div>
    </section>
    <section v-else-if="!completed" class="assessment-loading-card" aria-live="polite">
      <div class="loading-topline">
        <div class="loading-status">
          <span class="live-dot" aria-hidden="true" />
          <span>{{ taskState === 'QUEUED' ? '智评任务已排队' : '公平智评流程运行中' }}</span>
        </div>
        <span class="loading-refresh">页面会自动更新</span>
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
          <h2>AI 正在逐项智评作品</h2>
          <div class="current-check">
            <span class="current-check-index">{{ String(currentStageIndex + 1).padStart(2, '0') }}</span>
            <span class="current-check-copy">
              <small>当前检查</small>
              <strong>{{ currentStage.title }}</strong>
              <span>{{ currentStage.description }}</span>
            </span>
            <span class="current-check-pulse" aria-hidden="true" />
          </div>
        </div>
      </div>

      <div class="loading-progress">
        <div class="progress-heading">
          <span>智评进度</span>
          <strong>{{ progressPercent }}%</strong>
        </div>
        <div
          class="progress-track"
          role="progressbar"
          aria-label="AI 智评进度"
          aria-valuemin="0"
          aria-valuemax="100"
          :aria-valuenow="progressPercent"
        >
          <span class="progress-fill" :style="{ transform: `scaleX(${progressPercent / 100})` }" />
          <span class="progress-sheen" aria-hidden="true" />
        </div>
        <div class="progress-foot">
          <span>{{ taskState === 'QUEUED' ? '正在准备评测资源' : '评测结果将自动保存' }}</span>
          <span>最终结果由评委复核确认</span>
        </div>
      </div>

      <div class="loading-principles">
        <div v-for="(stage, index) in assessmentStages.slice(0, 3)" :key="stage.title" class="principle-item">
          <span class="principle-index">{{ String(index + 1).padStart(2, '0') }}</span>
          <span>
            <b>{{ stage.title }}</b>
            <small>{{ stage.description }}</small>
          </span>
        </div>
      </div>
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
</style>
