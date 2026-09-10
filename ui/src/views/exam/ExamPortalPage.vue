<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { EyeOutlined, PaperClipOutlined, CheckCircleOutlined, ClockCircleOutlined } from '@ant-design/icons-vue'
import { useRouter, useRoute } from 'vue-router'
import {
  getCandidateProgress,
  issueSsoTicket,
  listActiveCampaigns,
  listCandidateAgents,
  listCandidateQuestions,
  listSubmissions,
  createSubmission,
  deleteSubmission,
  type CandidateProgress,
  type ExamAgentSummaryVO,
  type ExamCampaign,
  type ExamQuestion,
  type ExamSubmission,
} from '@/api/exam'
import ExamPrestartPage from './ExamPrestartPage.vue'
import CandidateScorePage from './CandidateScorePage.vue'
import { download as downloadAttach } from '@/api/attach'
import { useAccountStore } from '@/stores'
import { decodeSubmissionForm } from '@/views/exam/form'

const router = useRouter()
const route = useRoute()
const accountStore = useAccountStore()

const campaigns = ref<ExamCampaign[]>([])
const campaign = ref<ExamCampaign | null>(null)
const progress = ref<CandidateProgress | null>(null)
const submissions = ref<ExamSubmission[]>([])
const loading = ref(false)
const addingWork = ref(false)
const agents = ref<ExamAgentSummaryVO[]>([])
const agentModal = ref(false)
const agentLoading = ref(false)
const agentGatewayModal = ref(false)
const gatewayLoading = ref(false)
const questions = ref<ExamQuestion[]>([])
const questionsLoading = ref(false)
const questionDetailVisible = ref(false)
const questionDetail = ref<ExamQuestion | null>(null)

/** 进度步骤：制作/提交 → AI 智评 → 评委发布 */
const steps = computed(() => {
  const p = progress.value
  const submitted = !!p?.submissionStatus && p.submissionStatus !== 'DRAFT'
  const aiDone = !!p?.submissionStatus && p.submissionStatus !== 'DRAFT'
  const published = !!p?.aiPublished
  return [
    { title: '制作/提交', desc: '制作智能体并提交作品', done: submitted, active: !submitted },
    { title: 'AI 智评', desc: 'AI 智评生成报告', done: aiDone, active: submitted && !published },
    { title: '评委发布', desc: '评委复核并发布成绩', done: published, active: aiDone && !published },
  ]
})

/** 开考倒计时（参考考试开始倒计时风格） */
function parseTime(value?: string): number | null {
  if (!value) return null
  const normalized = value.includes(' ') && !value.includes('T') ? value.replace(' ', 'T') : value
  const t = new Date(normalized).getTime()
  return Number.isNaN(t) ? null : t
}

const nowMs = ref(Date.now())
let clockTimer: number | undefined
onMounted(() => {
  clockTimer = window.setInterval(() => {
    nowMs.value = Date.now()
  }, 1000)
})
onBeforeUnmount(() => {
  if (clockTimer) window.clearInterval(clockTimer)
})

function pad(n: number): string {
  return String(n).padStart(2, '0')
}

const examStartMs = computed(() => parseTime(progress.value?.examStartAt))
const examEndMs = computed(() => parseTime(progress.value?.examEndAt))
const remainingSeconds = computed(() => {
  if (examEndMs.value === null) return 0
  return Math.max(0, Math.ceil((examEndMs.value - nowMs.value) / 1000))
})
const examEnded = computed(
  () => progress.value?.examState === 'ENDED' || (examEndMs.value !== null && remainingSeconds.value === 0),
)
const examNotOpen = computed(
  () => examStartMs.value !== null && nowMs.value < examStartMs.value.valueOf(),
)
const canSubmit = computed(() => !examEnded.value && !examNotOpen.value)
const scoreQueryMode = computed(
  () => progress.value?.scoreQueryEnabled === true || campaign.value?.scoreQueryEnabled === true,
)
const remainCountdown = computed(() => {
  const total = remainingSeconds.value
  return {
    hours: Math.floor(total / 3600),
    minutes: Math.floor((total % 3600) / 60),
    seconds: total % 60,
  }
})
const endLabel = computed(() => {
  if (examEndMs.value === null) return '待定'
  const d = new Date(examEndMs.value)
  return `${d.getFullYear()}年${pad(d.getMonth() + 1)}月${pad(d.getDate())}日 ${pad(d.getHours())}:${pad(d.getMinutes())}`
})

async function loadCampaigns() {
  loading.value = true
  try {
    const res = await listActiveCampaigns()
    campaigns.value = res.data.data || []
    campaign.value = campaigns.value[0] || null
    if (campaign.value) await loadProgress()
  } finally {
    loading.value = false
  }
}

const reachedWorkLimit = computed(() => {
  if (!questions.value.length) return false
  return submissions.value.length >= questions.value.length
})

async function loadProgress() {
  if (!campaign.value) return
  const res = await getCandidateProgress(campaign.value.id as string)
  progress.value = res.data.data || null
  if (progress.value?.examState) {
    sessionStorage.setItem('exam:window-state', progress.value.examState)
  } else {
    sessionStorage.removeItem('exam:window-state')
  }
  if (progress.value?.scoreQueryEnabled) {
    sessionStorage.setItem('exam:score-query-mode', '1')
  } else {
    sessionStorage.removeItem('exam:score-query-mode')
  }
  if (progress.value?.submissionId) sessionStorage.setItem('exam:submissionId', String(progress.value.submissionId))
  sessionStorage.setItem('exam:campaignId', String(campaign.value?.id || ''))
  sessionStorage.setItem('exam:candidate-mode', '1')
  sessionStorage.setItem(
    'exam:candidate-account',
    String(accountStore.userInfo?.id || accountStore.userInfo?.username || ''),
  )
  sessionStorage.setItem('exam:rubricConfig', campaign.value?.configJson || '')
  window.dispatchEvent(new Event('exam:context-updated'))
  await loadSubmissions()
  await loadQuestions()
}

async function loadQuestions() {
  if (!campaign.value) return
  questionsLoading.value = true
  try {
    const res = await listCandidateQuestions(campaign.value.id as string)
    questions.value = res.data.data || []
  } catch {
    questions.value = []
  } finally {
    questionsLoading.value = false
  }
}

async function loadSubmissions() {
  const eid = progress.value?.enrollmentId
  if (!eid) {
    submissions.value = []
    return
  }
  try {
    const res = await listSubmissions(eid)
    submissions.value = res.data?.data || []
  } catch {
    submissions.value = []
  }
}

async function removeWork(item: ExamSubmission) {
  Modal.confirm({
    title: '删除草稿作品',
    content: `确定要删除「${workTitle(item)}」吗？删除后不可恢复。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteSubmission(item.id as string | number)
        message.success('草稿已删除')
        await loadSubmissions()
      } catch {
        message.error('删除失败，请稍后重试')
      }
    },
  })
}

/** 同步打开一个空白新窗口（在用户点击手势内调用，避免被拦截），并带入考试上下文 */
function openBlankWindow(): Window | null {
  const w = window.open('', '_blank')
  if (!w) {
    message.warning('已尝试打开新窗口，若被浏览器拦截请允许弹出窗口')
    return null
  }
  try {
    const ck = sessionStorage.getItem('exam:campaignId')
    const sk = sessionStorage.getItem('exam:submissionId')
    if (ck) w.sessionStorage.setItem('exam:campaignId', ck)
    if (sk) w.sessionStorage.setItem('exam:submissionId', sk)
    w.sessionStorage.setItem('exam:candidate-mode', '1')
  } catch {
    // 跨域或受限时忽略，页面仍可凭 URL 参数加载
  }
  return w
}

/** 在新窗口打开考试路由页面（不覆盖当前门户） */
function openInNewTab(name: string, params: Record<string, string>) {
  const resolved = router.resolve({ name, params })
  const w = openBlankWindow()
  if (w) w.location.href = resolved.href
}

async function addWork(questionId?: string | number) {
  if (examEnded.value) {
    message.warning('考试已结束，不能再提交作品')
    return
  }
  if (examNotOpen.value) {
    message.warning('考试尚未开始，请等待开考后再提交作品')
    return
  }
  const eid = progress.value?.enrollmentId
  if (!eid) {
    message.warning('请先报名')
    return
  }
  if (!questionId) {
    message.warning('请选择要提交的试题')
    return
  }
  addingWork.value = true
  const w = openBlankWindow()
  try {
    const res = await createSubmission(eid, questionId)
    const sub = res.data?.data
    await loadSubmissions()
    if (sub?.id) {
      const resolved = router.resolve({ name: 'CandidateSubmission', params: { submissionId: String(sub.id) } })
      if (w) w.location.href = resolved.href
      else window.open(resolved.href, '_blank')
    } else if (w) {
      w.close()
    }
  } catch {
    if (w) w.close()
    message.error('新增作品失败，请稍后重试')
  } finally {
    addingWork.value = false
  }
}

function workTitle(item: ExamSubmission): string {
  return decodeSubmissionForm(item.formJson).work.title || '未命名作品'
}

function submissionForQuestion(qId: string | number): ExamSubmission | undefined {
  return submissions.value.find(s => String(s.questionId) === String(qId))
}

function questionStatus(qId: string | number): { text: string; color: string } {
  const sub = submissionForQuestion(qId)
  if (!sub) return { text: '未提交', color: 'default' }
  const map: Record<string, { text: string; color: string }> = {
    DRAFT: { text: '草稿', color: 'default' },
    AI_QUEUED: { text: '已提交', color: 'processing' },
    TIMEOUT_SUBMITTED: { text: '已提交', color: 'processing' },
    REVIEW_PENDING: { text: '待复核', color: 'warning' },
    PUBLISHED: { text: '已发布', color: 'success' },
  }
  return map[sub.status || 'DRAFT'] || { text: sub.status || '草稿', color: 'default' }
}

function openQuestionDetail(q: ExamQuestion) {
  questionDetail.value = q
  questionDetailVisible.value = true
}

function formatFileSize(bytes: number | null) {
  if (!bytes) return ''
  const mb = bytes / 1024 / 1024
  if (mb >= 1) return `${mb.toFixed(1)} MB`
  return `${Math.round(bytes / 1024)} KB`
}

async function downloadAttachment(q: ExamQuestion) {
  if (!q.attachmentUrl) return
  try {
    const res = await downloadAttach(q.attachmentUrl)
    const blob = new Blob([res.data])
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = q.attachmentName || 'attachment'
    a.click()
    window.URL.revokeObjectURL(url)
  } catch {
    message.error('下载失败')
  }
}

function submissionStatusText(status?: string): string {
  return (
    {
      DRAFT: '草稿',
      AI_QUEUED: '已提交',
      TIMEOUT_SUBMITTED: '已提交',
      REVIEW_PENDING: '待评委复核',
      PUBLISHED: '成绩已发布',
    }[status || 'DRAFT'] || status || '草稿'
  )
}

function goSubmission(id?: string | number) {
  if (progress.value?.examState === 'WAITING') {
    message.info(`考试将于 ${fmt(progress.value.examStartAt)} 开始，请先耐心等待`)
    return
  }
  // 作品列表按创建时间正序返回，默认应打开服务端确认的最新作品，不能取第一条旧记录。
  const fallbackId = submissions.value[submissions.value.length - 1]?.id
  const target = id ?? progress.value?.submissionId ?? fallbackId
  if (!target) {
    message.warning('请先报名并提交作品')
    return
  }
  openInNewTab('CandidateSubmission', { submissionId: String(target) })
}

function goAssessment(id?: string | number) {
  const fallbackId = submissions.value[submissions.value.length - 1]?.id
  const target = id ?? progress.value?.submissionId ?? fallbackId
  if (!target) return
  openInNewTab('CandidateAssessment', { submissionId: String(target) })
}

function handleAssessmentNav() {
  if (!progress.value?.submissionId || progress.value.submissionStatus === 'DRAFT') {
    message.warning('提交作品后即可查看 AI 智评进度与报告')
    return
  }
  goAssessment()
}

async function openMyAgents() {
  if (!campaign.value) return
  agentLoading.value = true
  agentModal.value = true
  try {
    const res = await listCandidateAgents(campaign.value.id as string)
    agents.value = res.data.data || []
  } catch {
    message.error('无法加载我的智能体')
  } finally {
    agentLoading.value = false
  }
}

function goMakeAgent() {
  if (!progress.value?.submissionOpen) {
    if (progress.value?.examState === 'WAITING') {
      message.info(`考试将于 ${fmt(progress.value.examStartAt)} 开始，请先耐心等待`)
    } else {
      message.warning('本次考试时间已结束')
    }
    return
  }
  agentGatewayModal.value = true
}

async function enterAgentPlatform() {
  if (!campaign.value) return message.warning('未获取到考试批次，请稍后重试')
  gatewayLoading.value = true
  try {
    const returnUrl = '/#/exam/portal'
    const response = await issueSsoTicket(campaign.value.id as string, returnUrl)
    const ticket = response.data.data?.ticket
    if (!ticket) throw new Error('未获取到 SSO 凭证')
    await router.push({ path: '/agent', query: { examTicket: ticket } })
    agentGatewayModal.value = false
  } catch {
    message.error('进入智能体平台失败，请稍后重试')
  } finally {
    gatewayLoading.value = false
  }
}

/** 顶部按钮：进入智能体制作平台 */
function handleMakeAgent() {
  goMakeAgent()
}

/** 顶部按钮：进入作品提交页 */
function handleSubmit() {
  goSubmission()
}

const statusTag = computed(() => {
  const s = progress.value?.submissionStatus
  return (
    {
      DRAFT: { text: '草稿', color: 'blue' },
      AI_QUEUED: { text: 'AI 智评中', color: 'processing' },
      REVIEW_PENDING: { text: '待评委复核', color: 'gold' },
      PUBLISHED: { text: '成绩已发布', color: 'success' },
      TIMEOUT_SUBMITTED: { text: '时间到，已自动交卷', color: 'orange' },
    }[s || 'DRAFT'] || { text: s || '草稿', color: 'blue' }
  )
})

function fmt(ts?: string) {
  if (!ts) return '待定'
  return new Date(ts).toLocaleString('zh-CN', { hour12: false })
}

/** 我的考试进度卡标题（对齐原型文案） */
const progressTitle = computed(() => {
  const p = progress.value
  if (!p) return '等待提交作品'
  if (p.submissionStatus === 'TIMEOUT_SUBMITTED') return '时间到，作品已自动交卷'
  if (p.submissionStatus && p.submissionStatus !== 'DRAFT') return p.aiPublished ? '成绩已发布' : 'AI 智评已完成'
  return '等待提交作品'
})

/** 处理顶栏「我的作品 / AI 智评」跳转：进入门户页后自动打开对应流程 */
function handleQueryAction() {
  const action = route.query.action
  if (action === 'submission') goSubmission()
  else if (action === 'assessment') goAssessment()
}

function refreshWindowState() {
  void loadProgress()
}

async function enterExamFromPrestart() {
  await loadProgress()
  if (progress.value?.examState === 'WAITING') {
    message.info(`考试将于 ${fmt(progress.value.examStartAt)} 开始，请先耐心等待`)
    return
  }
  goSubmission()
}

onMounted(async () => {
  window.addEventListener('exam:window-state-updated', refreshWindowState)
  await loadCampaigns()
  handleQueryAction()
})

onBeforeUnmount(() => {
  window.removeEventListener('exam:window-state-updated', refreshWindowState)
})

watch(
  () => route.query.action,
  () => handleQueryAction(),
)
</script>

<template>
  <main class="exam-portal">
    <section v-if="loading" class="portal-loading">
      <a-spin tip="加载考试信息…" />
    </section>

    <a-empty v-else-if="!campaign" description="当前没有进行中的考试批次" class="portal-empty" />

    <ExamPrestartPage
      v-else-if="progress?.examState === 'WAITING'"
      :campaign="campaign"
      :progress="progress"
      @exam-started="refreshWindowState"
      @enter-exam="enterExamFromPrestart"
    />

    <CandidateScorePage
      v-else-if="scoreQueryMode"
      :campaign="campaign"
      :progress="progress"
    />

    <template v-else>
      <!-- 英雄横幅 + 我的考试进度 -->
      <header class="hero">
        <div class="hero-main">
          <p class="eyebrow">{{ campaign.name }}</p>
          <h1>把你的智能体作品，<br /><em>交给专业评审。</em></h1>
          <p class="lead">
            欢迎进入本次考试，祝您取得优异成绩！请按流程完成智能体制作、作品提交与评审。
          </p>
          <a-alert
            v-if="progress?.examState === 'ENDED' && progress.submissionStatus === 'DRAFT'"
            type="warning"
            show-icon
            message="考试时间已结束"
            description="未提交的作品已由系统按截止时间处理。"
            class="window-alert"
          />
          <div class="actions">
            <a-button class="p-btn p-btn-primary" type="primary" size="large" :disabled="!canSubmit" @click="handleMakeAgent">
              去制作智能体
            </a-button>
          </div>
        </div>

        <aside class="progress progress--countdown">
          <p class="eyebrow">我的考试进度</p>
          <template v-if="!examEnded">
            <div class="countdown-heading">距离考试结束还有</div>
            <div class="countdown-row" aria-label="考试倒计时">
              <div class="time-block"><span class="time-value">{{ pad(remainCountdown.hours) }}</span><span class="time-label">时</span></div>
              <div class="time-block"><span class="time-value">{{ pad(remainCountdown.minutes) }}</span><span class="time-label">分</span></div>
              <div class="time-block"><span class="time-value">{{ pad(remainCountdown.seconds) }}</span><span class="time-label">秒</span></div>
            </div>
            <div class="countdown-footnote">结束时间：{{ endLabel }}</div>
          </template>
          <template v-else>
            <div class="countdown-heading">考试已结束</div>
            <div class="countdown-footnote">本场考试作答时间已结束，系统将按截止时间处理作品。</div>
          </template>
        </aside>
      </header>

      <!-- 试题列表 -->
      <section v-if="progress?.enrolled" class="section works">
        <p class="eyebrow">考试试题</p>
        <h2>共 {{ questions.length }} 道试题，请逐一完成提交</h2>
        <a-spin :spinning="questionsLoading" />
        <a-empty v-if="!questionsLoading && !questions.length" description="暂未配置试题，请联系管理员" class="works-empty" />
        <div v-else class="question-card-list">
          <div v-for="(q, idx) in questions" :key="q.id" class="question-card-item" :class="{
            'card-submitted': submissionForQuestion(q.id!) && submissionForQuestion(q.id!)!.status !== 'DRAFT',
            'card-draft': submissionForQuestion(q.id!) && submissionForQuestion(q.id!)!.status === 'DRAFT',
            'card-pending': !submissionForQuestion(q.id!)
          }">
            <div class="question-card-body">
              <div class="question-card-left">
                <div class="question-card-index" :class="submissionForQuestion(q.id!) ? 'done' : 'pending'">{{ idx + 1 }}</div>
                <div class="question-card-info">
                  <div class="question-card-title">
                    <span class="question-title-link" @click="openQuestionDetail(q)">
                      {{ q.title }}
                      <EyeOutlined style="margin-left:4px;font-size:12px;" />
                    </span>
                  </div>
                  <div class="question-card-meta">
                    <span><i class="fa fa-star" style="margin-right:4px;"></i>分值：{{ q.score ?? '—' }}分</span>
                    <span v-if="q.taskScenario"><i class="fa fa-tag" style="margin-right:4px;"></i>场景：{{ q.taskScenario }}</span>
                  </div>
                </div>
              </div>
              <div class="question-card-actions">
                <span v-if="submissionForQuestion(q.id!)" class="status-badge" :class="submissionForQuestion(q.id!)!.status === 'DRAFT' ? 'status-draft' : 'status-submitted'">
                  <CheckCircleOutlined v-if="submissionForQuestion(q.id!)!.status !== 'DRAFT'" />
                  <ClockCircleOutlined v-else />
                  {{ submissionForQuestion(q.id!)!.status === 'DRAFT' ? '草稿' : '已提交' }}
                </span>
                <span v-else class="status-badge status-pending">
                  <ClockCircleOutlined />
                  待提交
                </span>
                <template v-if="submissionForQuestion(q.id!)">
                  <a-button v-if="submissionForQuestion(q.id!)!.status === 'DRAFT'" type="primary" ghost size="small" @click="goSubmission(submissionForQuestion(q.id!)!.id)">
                    继续编辑
                  </a-button>
                  <a-button v-else type="primary" ghost size="small" @click="goSubmission(submissionForQuestion(q.id!)!.id)">
                    查看作品
                  </a-button>
                  <a-button v-if="submissionForQuestion(q.id!)!.status === 'DRAFT'" type="link" size="small" danger @click="removeWork(submissionForQuestion(q.id!)!)">
                    删除
                  </a-button>
                  <a-button v-if="submissionForQuestion(q.id!)!.status && submissionForQuestion(q.id!)!.status !== 'DRAFT'" type="link" size="small" @click="goAssessment(submissionForQuestion(q.id!)!.id)">
                    AI 智评
                  </a-button>
                </template>
                <a-button v-else type="primary" size="small" :loading="addingWork" :disabled="!canSubmit" @click="addWork(q.id)">
                  提交作品
                </a-button>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 试题详情弹窗 -->
      <a-modal v-model:open="questionDetailVisible" title="试题详情" :footer="null" width="640px">
        <template v-if="questionDetail">
          <a-descriptions :column="1" size="small" bordered :label-style="{ width: '100px' }">
            <a-descriptions-item label="题目">{{ questionDetail.title }}</a-descriptions-item>
            <a-descriptions-item label="分值">{{ questionDetail.score }} 分</a-descriptions-item>
            <a-descriptions-item v-if="questionDetail.suggestedMinutes" label="建议用时">{{ questionDetail.suggestedMinutes }} 分钟</a-descriptions-item>
            <a-descriptions-item v-if="questionDetail.taskScenario" label="任务场景">{{ questionDetail.taskScenario }}</a-descriptions-item>
            <a-descriptions-item v-if="questionDetail.taskRequirement" label="任务要求">{{ questionDetail.taskRequirement }}</a-descriptions-item>
            <a-descriptions-item v-if="questionDetail.submissionDeliverable" label="提交成果">{{ questionDetail.submissionDeliverable }}</a-descriptions-item>
            <a-descriptions-item v-if="questionDetail.attachmentUrl" label="附件">
              <div class="detail-attachment">
                <PaperClipOutlined />
                <span>{{ questionDetail.attachmentName }}</span>
                <span v-if="questionDetail.attachmentSize" style="color:#6d7c94;font-size:12px;">{{ formatFileSize(questionDetail.attachmentSize) }}</span>
                <a-button type="link" size="small" @click="downloadAttachment(questionDetail)">下载</a-button>
              </div>
            </a-descriptions-item>
          </a-descriptions>
        </template>
      </a-modal>

      <!-- 考试流程 -->
      <section class="section">
        <p class="eyebrow">考试流程</p>
        <h2>从创作到成绩发布</h2>
        <div class="flow">
          <article>
            <i>01</i>
            <h3>制作智能体</h3>
            <p>进入智能体平台，完成场景、提示词、工具与知识库配置。</p>
          </article>
          <article>
            <i>02</i>
            <h3>向导提交</h3>
            <p>按步骤完成身份、作品信息和材料填报。</p>
          </article>
          <article>
            <i>03</i>
            <h3>AI 智评</h3>
            <p>采用 AI 按统一评分标准进行客观、公平的初步评分，结果由评委复核确认。</p>
          </article>
          <article>
            <i>04</i>
            <h3>评委复核</h3>
            <p>最终成绩以评委正式发布为准。</p>
          </article>
        </div>
      </section>
    </template>

    <!-- 我的智能体弹层 -->
    <a-modal v-model:open="agentModal" title="我的智能体" :footer="null" width="720px">
      <a-spin v-if="agentLoading" />
      <a-empty v-else-if="!agents.length" description="你还没有可带入考试的智能体" />
      <a-list v-else :data-source="agents" item-layout="horizontal">
        <template #renderItem="{ item }">
          <a-list-item>
            <a-list-item-meta :title="item.name" :description="item.description" />
            <template #actions>
              <a-tag v-for="s in (item.skills || []).slice(0, 3)" :key="s" color="blue">{{ s }}</a-tag>
            </template>
          </a-list-item>
        </template>
      </a-list>
    </a-modal>
    <a-modal v-model:open="agentGatewayModal" title="进入智能体制作平台" :footer="null" width="620px" centered>
      <div class="gateway-modal">
        <span class="gateway-icon">AI</span>
        <h2>先完成作品制作，再返回提交</h2>
        <p>你将通过统一登录进入智能体平台，在“我的智能体”中创建或完善作品。完成后返回考试门户选择并提交该智能体。</p>
        <div class="gateway-security"><div><b>安全登录跳转</b><small>使用一次性 SSO Ticket，不会在地址栏暴露登录凭证或模型密钥。</small></div><span>5 分钟有效</span></div>
        <div class="gateway-actions"><a-button @click="agentGatewayModal = false">暂不进入</a-button><a-button type="primary" :loading="gatewayLoading" @click="enterAgentPlatform">进入智能体平台</a-button></div>
      </div>
    </a-modal>
  </main>
</template>

<style scoped lang="scss">
.exam-portal {
  max-width: 1180px;
  margin: 0 auto;
  padding: 32px 20px 56px;
}

.exam-portal :deep(.portal-topbar) {
  margin: -32px -20px 28px;
  border-radius: 0;
}

.eyebrow {
  color: rgba(255, 255, 255, 0.82);
  letter-spacing: 0.16em;
  font-size: 12px;
  margin: 0 0 10px;
}

/* 英雄横幅 + 我的考试进度 */
.hero {
  display: grid;
  grid-template-columns: 1.5fr 1fr;
  gap: 28px;
  border-radius: 18px;
  padding: 44px 40px;
  color: #fff;
  background: radial-gradient(120% 140% at 0% 0%, #3b6bff 0%, #5b3cff 55%, #7a3cff 100%);
  box-shadow: 0 18px 44px rgba(58, 70, 180, 0.34);
}

.hero-main h1 {
  font-size: 32px;
  line-height: 1.28;
  margin: 0 0 14px;
  font-weight: 700;
}

.hero-main h1 em {
  font-style: normal;
  color: #ffd76a;
}

.lead {
  max-width: 440px;
  opacity: 0.9;
  margin: 0 0 24px;
  line-height: 1.7;
  font-size: 15px;
}

.window-alert {
  max-width: 500px;
  margin: 0 0 22px;
  color: #14213d;
  text-align: left;
}

.actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

/* 对齐原型 .btn / .btn.alt */
.p-btn {
  height: 46px;
  padding: 0 26px;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 600;
  box-shadow: none;
}

.p-btn-primary.ant-btn-primary {
  background: #ffffff;
  color: #5336e0;
  border-color: #ffffff;
}

.p-btn-primary.ant-btn-primary:hover,
.p-btn-primary.ant-btn-primary:focus {
  background: #f0f0ff;
  color: #5336e0;
  border-color: #f0f0ff;
}

.p-btn-alt {
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.55);
}

.p-btn-alt:hover,
.p-btn-alt:focus {
  background: rgba(255, 255, 255, 0.22) !important;
  color: #fff !important;
  border-color: #fff !important;
}

/* 进度卡 */
.progress {
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 14px;
  padding: 20px 20px 22px;
  backdrop-filter: blur(6px);
}

.progress .eyebrow {
  margin-bottom: 6px;
}

.progress h2 {
  margin: 0 0 16px;
  font-size: 17px;
  font-weight: 600;
}

.p-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 9px 0;
  color: rgba(255, 255, 255, 0.82);
  font-size: 14px;
}

.p-item .dot {
  width: 22px;
  height: 22px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
}

.p-item.done {
  color: #fff;
}

.p-item.done .dot {
  background: #34d27b;
}

/* 考试流程 */
.section {
  margin-top: 36px;
}

.works-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.works-count {
  color: #5a6477;
  font-size: 14px;
}
.works-list {
  background: #fff;
  border: 1px solid #e8edff;
  border-radius: 14px;
  padding: 6px 18px;
}

.works-empty {
  background: #fff;
  border: 1px solid #e8edff;
  border-radius: 14px;
  padding: 48px 18px;
}

.question-card-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.question-card-item {
  background: #fff;
  border: 1px solid #e8edff;
  border-radius: 14px;
  padding: 20px 24px;
  transition: box-shadow 0.2s, background-color 0.2s;
}
.question-card-item:hover {
  box-shadow: 0 2px 12px rgba(75, 132, 217, 0.08);
}
.question-card-item.card-submitted {
  background: #f0f7ff;
  border-color: #bdd7ff;
}
.question-card-item.card-draft {
  background: #fffbe6;
  border-color: #ffe58f;
}
.question-card-item.card-pending {
  background: #fff;
}
.question-card-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
@media (min-width: 768px) {
  .question-card-body {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
  }
}
.question-card-left {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}
.question-card-index {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 14px;
  flex-shrink: 0;
  margin-top: 2px;
}
.question-card-index.done {
  background: rgba(75, 132, 217, 0.1);
  color: #4b84d9;
}
.question-card-index.pending {
  background: #f1f5f9;
  color: #94a3b8;
}
.question-card-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.question-card-title {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}
.question-card-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 13px;
  color: #64748b;
}
.question-card-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 12px;
  border-radius: 20px;
  font-size: 15px;
  font-weight: 600;
}
.status-submitted {
  background: #e6f7e6;
  color: #389e0d;
}
.status-draft {
  background: #fff7e6;
  color: #d48806;
}
.status-pending {
  background: #f1f5f9;
  color: #94a3b8;
}
.question-title-link {
  cursor: pointer;
  color: #4b84d9;
  font-weight: 500;
  text-decoration: none;
  transition: color 0.2s;
}
.question-title-link:hover {
  color: #1e40af;
  text-decoration: underline;
}
.detail-attachment {
  display: flex;
  align-items: center;
  gap: 8px;
}

.section .eyebrow {
  color: #4c6ef5;
}

.section h2 {
  margin: 0 0 22px;
  font-size: 22px;
}

.flow {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.flow article {
  background: #f6f8ff;
  border: 1px solid #e8edff;
  border-radius: 14px;
  padding: 20px 18px;
}
.gateway-modal{text-align:center;padding:8px 6px}.gateway-icon{display:inline-grid;place-items:center;width:50px;height:50px;border-radius:14px;background:linear-gradient(135deg,#1681ff,#1749ba);color:#fff;font-weight:800}.gateway-modal h2{margin:17px 0 10px;color:#0a1e45}.gateway-modal>p{line-height:1.75;color:#6d7c94}.gateway-security{display:flex;justify-content:space-between;align-items:center;text-align:left;background:#eaf3ff;border:1px solid #d2e4ff;border-radius:10px;padding:16px;margin:22px 0}.gateway-security b,.gateway-security small{display:block}.gateway-security small{margin-top:5px;color:#6d7c94}.gateway-security>span{font-size:12px;color:#2d68b4}.gateway-actions{display:flex;justify-content:center;gap:12px}

.flow article i {
  font-style: normal;
  font-size: 26px;
  font-weight: 800;
  color: #4c6ef5;
  letter-spacing: 0.04em;
}

.flow article h3 {
  margin: 10px 0 8px;
  font-size: 16px;
}

.flow article p {
  margin: 0;
  color: #5a6477;
  font-size: 13px;
  line-height: 1.65;
}

.portal-loading,
.portal-empty {
  margin-top: 60px;
  text-align: center;
}

.progress--countdown {
  background: #172b52;
  border-color: rgba(190, 211, 255, 0.18);
  color: #fff;
}

.progress--countdown .eyebrow {
  color: #9fb3dc;
}

.countdown-heading {
  margin: 4px 0 16px;
  color: #fff;
  font-size: 18px;
  font-weight: 500;
}

.countdown-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.time-block {
  padding: 15px 12px 13px;
  text-align: center;
  background: rgba(255, 255, 255, 0.095);
  border: 1px solid rgba(190, 211, 255, 0.13);
  border-radius: 13px;
}

.time-value {
  display: block;
  color: #fff;
  font-size: clamp(28px, 4.2vw, 44px);
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  letter-spacing: -0.04em;
  line-height: 1;
}

.time-label {
  display: block;
  margin-top: 8px;
  color: #9fb3dc;
  font-size: 11px;
}

.countdown-footnote {
  margin-top: 14px;
  color: #91a6d0;
  font-size: 12px;
}

@media (max-width: 900px) {
  .hero {
    grid-template-columns: 1fr;
  }
  .flow {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
