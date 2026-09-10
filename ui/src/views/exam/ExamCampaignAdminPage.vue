<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import dayjs, { type Dayjs } from 'dayjs'
import { message } from 'ant-design-vue'
import {
  CalendarOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  EditOutlined,
  EyeOutlined,
  PlusOutlined,
  ReloadOutlined,
  FileTextOutlined,
  DeleteOutlined,
  UploadOutlined,
  PaperClipOutlined,
  PlayCircleOutlined,
} from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import { useAccountStore } from '@/stores'
import {
  createExamCampaign,
  listExamCampaignsForAdmin,
  updateExamCampaign,
  updateExamCampaignEnabled,
  updateExamCampaignScoreQuery,
  startExamNow,
  listExamQuestions,
  createExamQuestion,
  updateExamQuestion,
  deleteExamQuestion,
  type ExamCampaign,
  type ExamCampaignUpdateRequest,
  type ExamQuestion,
} from '@/api/exam'
import { RouteNames } from '@/router'
import { upload as uploadFile } from '@/api/attach'

type CampaignState = 'DISABLED' | 'DRAFT' | 'UNSCHEDULED' | 'WAITING' | 'OPEN' | 'ENDED' | 'CLOSED'

interface CampaignForm {
  name: string
  startAt: Dayjs | null
  durationMinutes: number | null
  enabled: boolean
}

const router = useRouter()
const accountStore = useAccountStore()
const loading = ref(false)
const rows = ref<ExamCampaign[]>([])
const nowMs = ref(Date.now())
const editVisible = ref(false)
const editingId = ref<string | null>(null)
const saving = ref(false)
const togglingId = ref<string | null>(null)
const startingId = ref<string | null>(null)
const scoreQueryTogglingId = ref<string | null>(null)
const form = reactive<CampaignForm>({
  name: '',
  startAt: null,
  durationMinutes: 120,
  enabled: true,
})

// 试题管理
const questionVisible = ref(false)
const questionCampaignId = ref<string | null>(null)
const questionCampaignName = ref('')
const questionLoading = ref(false)
const questions = ref<ExamQuestion[]>([])
const questionFormVisible = ref(false)
const questionSaving = ref(false)
const editingQuestionId = ref<string | null>(null)
const questionForm = reactive({
  title: '',
  score: 0,
  suggestedMinutes: null as number | null,
  taskScenario: '',
  taskRequirement: '',
  submissionDeliverable: '',
  sortOrder: 0,
  attachmentUrl: '',
  attachmentName: '',
  attachmentSize: null as number | null,
})
const questionFileUploading = ref(false)

let clockTimer: number | undefined

const isPlatformAdmin = computed(
  () => accountStore.userInfo?.username?.toLowerCase() === 'admin',
)
const isCreating = computed(() => editingId.value === null)
const modalTitle = computed(() => isCreating.value ? '新建场次' : '编辑场次')

const calculatedEndAt = computed(() => {
  const duration = form.durationMinutes
  if (!form.startAt || typeof duration !== 'number' || !Number.isInteger(duration) || duration < 1) return null
  return form.startAt.add(duration, 'minute')
})
const calculatedEndLabel = computed(() => (
  calculatedEndAt.value
    ? calculatedEndAt.value.format('YYYY-MM-DD HH:mm:ss')
    : '填写开考时间和限时后自动计算'
))

function parseTime(value?: string): Dayjs | null {
  if (!value) return null
  const normalized = value.includes(' ') && !value.includes('T') ? value.replace(' ', 'T') : value
  const parsed = dayjs(normalized)
  return parsed.isValid() ? parsed : null
}

function formatTime(value?: string): string {
  const parsed = parseTime(value)
  return parsed ? parsed.format('YYYY-MM-DD HH:mm:ss') : '未设置'
}

function durationOf(campaign: ExamCampaign): number | null {
  const start = parseTime(campaign.submissionStart)
  const end = parseTime(campaign.submissionEnd)
  if (!start || !end) return null
  return Math.max(1, Math.round(end.diff(start, 'minute', true)))
}

function isCampaignEnabled(campaign: ExamCampaign): boolean {
  return campaign.enabled !== false
}

function isScoreQueryEnabled(campaign: ExamCampaign): boolean {
  return campaign.scoreQueryEnabled === true
}

function campaignState(campaign: ExamCampaign): CampaignState {
  if (!isCampaignEnabled(campaign)) return 'DISABLED'
  if (campaign.status === 'DRAFT') return 'DRAFT'
  if (campaign.status === 'CLOSED') return 'CLOSED'

  const start = parseTime(campaign.submissionStart)
  if (!start) return 'UNSCHEDULED'
  if (nowMs.value < start.valueOf()) return 'WAITING'

  const end = parseTime(campaign.submissionEnd)
  if (end && nowMs.value >= end.valueOf()) return 'ENDED'
  return 'OPEN'
}

function stateLabel(state: CampaignState): string {
  return {
    DISABLED: '已停用',
    DRAFT: '草稿',
    UNSCHEDULED: '待设置',
    WAITING: '未开始',
    OPEN: '进行中',
    ENDED: '已结束',
    CLOSED: '已关闭',
  }[state]
}

function stateColor(state: CampaignState): string {
  return {
    DISABLED: 'default',
    DRAFT: 'gold',
    UNSCHEDULED: 'orange',
    WAITING: 'blue',
    OPEN: 'green',
    ENDED: 'default',
    CLOSED: 'default',
  }[state]
}

const statusCounts = computed(() => ({
  waiting: rows.value.filter((item) => campaignState(item) === 'WAITING').length,
  open: rows.value.filter((item) => campaignState(item) === 'OPEN').length,
  ended: rows.value.filter((item) => campaignState(item) === 'ENDED').length,
}))

async function load() {
  if (!isPlatformAdmin.value) {
    await router.replace({ name: RouteNames.EXPLORE })
    return
  }
  loading.value = true
  try {
    const response = await listExamCampaignsForAdmin()
    rows.value = response.data.data || []
  } catch {
    message.error('加载考试批次失败')
  } finally {
    loading.value = false
  }
}

function replaceRow(updated: ExamCampaign | undefined) {
  if (!updated?.id) return
  const index = rows.value.findIndex((item) => String(item.id) === String(updated.id))
  if (index >= 0) rows.value[index] = updated
}

function resetForm() {
  form.name = ''
  form.startAt = dayjs().add(1, 'hour').startOf('minute')
  form.durationMinutes = 120
  form.enabled = true
}

function openCreate() {
  editingId.value = null
  resetForm()
  editVisible.value = true
}

function openEdit(campaign: ExamCampaign) {
  if (!campaign.id) return
  editingId.value = String(campaign.id)
  form.name = campaign.name || ''
  form.startAt = parseTime(campaign.submissionStart)
  form.durationMinutes = durationOf(campaign) || 120
  form.enabled = isCampaignEnabled(campaign)
  editVisible.value = true
}

async function saveCampaign() {
  const name = form.name.trim()
  const startAt = form.startAt
  const durationMinutes = form.durationMinutes

  if (!name) {
    message.warning('请填写考试名称')
    return
  }
  if (name.length > 200) {
    message.warning('考试名称不能超过 200 个字符')
    return
  }
  if (!startAt) {
    message.warning('请选择开考时间')
    return
  }
  if (
    typeof durationMinutes !== 'number'
    || !Number.isInteger(durationMinutes)
    || durationMinutes < 1
    || durationMinutes > 10080
  ) {
    message.warning('考试时长需为 1～10080 分钟')
    return
  }

  const payload: ExamCampaignUpdateRequest = {
    name,
    startAt: startAt.format('YYYY-MM-DD HH:mm:ss'),
    durationMinutes,
    enabled: form.enabled,
  }
  saving.value = true
  try {
    const response = isCreating.value
      ? await createExamCampaign(payload)
      : await updateExamCampaign(editingId.value as string, payload)
    const updated = response.data.data
    if (isCreating.value && updated) rows.value = [updated, ...rows.value]
    else replaceRow(updated)
    editVisible.value = false
    message.success(isCreating.value ? '考试批次已创建' : '考试批次已保存')
  } catch {
    message.error(isCreating.value ? '创建考试批次失败' : '保存考试批次失败')
  } finally {
    saving.value = false
  }
}

async function toggleEnabled(campaign: ExamCampaign) {
  if (!campaign.id) return
  const nextEnabled = !isCampaignEnabled(campaign)
  togglingId.value = String(campaign.id)
  try {
    const response = await updateExamCampaignEnabled(campaign.id, nextEnabled)
    replaceRow(response.data.data)
    message.success(nextEnabled ? '考试批次已启用' : '考试批次已停用')
  } catch {
    message.error(nextEnabled ? '启用考试批次失败' : '停用考试批次失败')
  } finally {
    togglingId.value = null
  }
}

async function startNow(campaign: ExamCampaign) {
  if (!campaign.id) return
  startingId.value = String(campaign.id)
  try {
    const response = await startExamNow(campaign.id)
    replaceRow(response.data.data)
    nowMs.value = Date.now()
    message.success('已立即开考，考生现在可以进入考试')
  } catch {
    message.error('立即开考失败，请先确认场次尚未结束')
  } finally {
    startingId.value = null
  }
}

async function toggleScoreQuery(campaign: ExamCampaign) {
  if (!campaign.id || campaignState(campaign) !== 'ENDED') return
  const nextEnabled = !isScoreQueryEnabled(campaign)
  scoreQueryTogglingId.value = String(campaign.id)
  try {
    const response = await updateExamCampaignScoreQuery(campaign.id, nextEnabled)
    replaceRow(response.data.data)
    message.success(nextEnabled ? '查分模式已开启' : '查分模式已关闭')
  } catch {
    message.error(nextEnabled ? '开启查分模式失败' : '关闭查分模式失败')
  } finally {
    scoreQueryTogglingId.value = null
  }
}

function goCandidateStatus(campaign: ExamCampaign) {
  if (!campaign.id) return
  void router.push({
    name: RouteNames.EXAM_CANDIDATE_STATUS,
    query: { campaignId: String(campaign.id) },
  })
}

// ─── 试题管理 ───
function openQuestions(campaign: ExamCampaign) {
  if (!campaign.id) return
  questionCampaignId.value = String(campaign.id)
  questionCampaignName.value = campaign.name || ''
  questionVisible.value = true
  void loadQuestions()
}

async function loadQuestions() {
  if (!questionCampaignId.value) return
  questionLoading.value = true
  try {
    const res = await listExamQuestions(questionCampaignId.value)
    questions.value = res.data.data || []
  } catch {
    message.error('加载试题失败')
  } finally {
    questionLoading.value = false
  }
}

function openCreateQuestion() {
  editingQuestionId.value = null
  questionForm.title = ''
  questionForm.score = 0
  questionForm.suggestedMinutes = null
  questionForm.taskScenario = ''
  questionForm.taskRequirement = ''
  questionForm.submissionDeliverable = ''
  questionForm.sortOrder = questions.value.length
  questionForm.attachmentUrl = ''
  questionForm.attachmentName = ''
  questionForm.attachmentSize = null
  questionFormVisible.value = true
}

function openEditQuestion(q: ExamQuestion) {
  editingQuestionId.value = q.id || null
  questionForm.title = q.title || ''
  questionForm.score = q.score ?? 0
  questionForm.suggestedMinutes = q.suggestedMinutes ?? null
  questionForm.taskScenario = q.taskScenario || ''
  questionForm.taskRequirement = q.taskRequirement || ''
  questionForm.submissionDeliverable = q.submissionDeliverable || ''
  questionForm.sortOrder = q.sortOrder ?? 0
  questionForm.attachmentUrl = q.attachmentUrl || ''
  questionForm.attachmentName = q.attachmentName || ''
  questionForm.attachmentSize = q.attachmentSize ?? null
  questionFormVisible.value = true
}

async function saveQuestion() {
  if (!questionForm.title.trim()) {
    message.warning('请填写题目')
    return
  }
  if (!questionCampaignId.value) return
  questionSaving.value = true
  try {
    const payload = {
      title: questionForm.title.trim(),
      score: questionForm.score,
      suggestedMinutes: questionForm.suggestedMinutes,
      taskScenario: questionForm.taskScenario,
      taskRequirement: questionForm.taskRequirement,
      submissionDeliverable: questionForm.submissionDeliverable,
      sortOrder: questionForm.sortOrder,
      attachmentUrl: questionForm.attachmentUrl || null,
      attachmentName: questionForm.attachmentName || null,
      attachmentSize: questionForm.attachmentSize,
    }
    if (editingQuestionId.value) {
      await updateExamQuestion(questionCampaignId.value, editingQuestionId.value, payload)
      message.success('试题已更新')
    } else {
      await createExamQuestion(questionCampaignId.value, payload)
      message.success('试题已添加')
    }
    questionFormVisible.value = false
    await loadQuestions()
  } catch {
    message.error('保存试题失败')
  } finally {
    questionSaving.value = false
  }
}

async function removeQuestion(q: ExamQuestion) {
  if (!questionCampaignId.value || !q.id) return
  try {
    await deleteExamQuestion(questionCampaignId.value, q.id)
    message.success('试题已删除')
    await loadQuestions()
  } catch {
    message.error('删除试题失败')
  }
}

function formatFileSize(bytes: number | null) {
  if (!bytes) return ''
  const mb = bytes / 1024 / 1024
  if (mb >= 1) return `${mb.toFixed(1)} MB`
  return `${Math.round(bytes / 1024)} KB`
}

async function handleQuestionFileUpload(options: { file: File }) {
  const file = options.file
  questionFileUploading.value = true
  try {
    const res = await uploadFile(file)
    const fileId = res.data.data
    questionForm.attachmentUrl = fileId
    questionForm.attachmentName = file.name
    questionForm.attachmentSize = file.size
    message.success('附件上传成功')
  } catch {
    message.error('附件上传失败')
  } finally {
    questionFileUploading.value = false
  }
}

function removeQuestionAttachment() {
  questionForm.attachmentUrl = ''
  questionForm.attachmentName = ''
  questionForm.attachmentSize = null
}

onMounted(() => {
  void load()
  clockTimer = window.setInterval(() => {
    nowMs.value = Date.now()
  }, 1000)
})

onBeforeUnmount(() => {
  if (clockTimer) window.clearInterval(clockTimer)
})
</script>

<template>
  <main class="exam-admin-page">
    <header class="page-heading">
      <div class="title-mark"><CalendarOutlined /></div>
      <div class="heading-copy">
        <span>场次管理</span>
        <h1>场次试题管理</h1>
        <p>管理考试场次、时间与试题配置。</p>
      </div>
      <div class="heading-actions">
        <a-button type="primary" @click="openCreate">
          <template #icon><PlusOutlined /></template>
          新建场次
        </a-button>
        <a-button :loading="loading" @click="load">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
      </div>
    </header>

    <a-alert
      type="info"
      show-icon
      message="时间窗口对本批次所有考生统一生效"
      description="考生会按照开考时间进入等待页，限时结束后系统自动收卷。"
      class="schedule-tip"
    />

    <section class="summary-grid">
      <a-card :bordered="false" class="summary-card">
        <span>全部考试</span>
        <strong>{{ rows.length }}</strong>
        <small>已创建的考试场次</small>
      </a-card>
      <a-card :bordered="false" class="summary-card waiting">
        <span>待开考</span>
        <strong>{{ statusCounts.waiting }}</strong>
        <small>考生可提前登录等待</small>
      </a-card>
      <a-card :bordered="false" class="summary-card open">
        <span>进行中</span>
        <strong>{{ statusCounts.open }}</strong>
        <small>当前开放答题</small>
      </a-card>
      <a-card :bordered="false" class="summary-card ended">
        <span>已结束</span>
        <strong>{{ statusCounts.ended }}</strong>
        <small>已到达结束时间</small>
      </a-card>
    </section>

    <a-card :bordered="false" class="campaign-card">
      <a-table
        :data-source="rows"
        :loading="loading"
        :pagination="false"
        row-key="id"
        :scroll="{ x: 1240 }"
      >
        <a-table-column title="考试名称" data-index="name" key="name" :width="240" fixed="left">
          <template #default="{ record }">
            <div class="campaign-name">{{ record.name || '未命名考试' }}</div>
            <div class="campaign-id">批次 ID：{{ record.id || '—' }}</div>
          </template>
        </a-table-column>
        <a-table-column title="状态" key="status" :width="110">
          <template #default="{ record }">
            <a-tag :color="stateColor(campaignState(record as ExamCampaign))">
              {{ stateLabel(campaignState(record as ExamCampaign)) }}
            </a-tag>
          </template>
        </a-table-column>
        <a-table-column title="开考时间" key="submissionStart" :width="185">
          <template #default="{ record }">{{ formatTime(record.submissionStart) }}</template>
        </a-table-column>
        <a-table-column title="结束时间" key="submissionEnd" :width="185">
          <template #default="{ record }">{{ formatTime(record.submissionEnd) }}</template>
        </a-table-column>
        <a-table-column title="限时" key="duration" :width="125">
          <template #default="{ record }">
            <span v-if="durationOf(record as ExamCampaign) !== null">
              <ClockCircleOutlined /> {{ durationOf(record as ExamCampaign) }} 分钟
            </span>
            <span v-else class="muted">未设置</span>
          </template>
        </a-table-column>
        <a-table-column title="开放状态" key="enabled" :width="105">
          <template #default="{ record }">
            <span :class="['enabled-state', { disabled: !isCampaignEnabled(record as ExamCampaign) }]">
              <CheckCircleOutlined />
              {{ isCampaignEnabled(record as ExamCampaign) ? '已启用' : '已停用' }}
            </span>
          </template>
        </a-table-column>
        <a-table-column title="查分模式" key="scoreQuery" :width="120">
          <template #default="{ record }">
            <a-tag v-if="isScoreQueryEnabled(record as ExamCampaign)" color="green">已开启</a-tag>
            <span v-else class="muted">未开启</span>
          </template>
        </a-table-column>
        <a-table-column title="操作" key="action" fixed="right" :width="490">
          <template #default="{ record }">
            <div class="action-links">
              <a-button type="link" size="small" @click="openEdit(record as ExamCampaign)">
                <EditOutlined /> 编辑
              </a-button>
              <a-button type="link" size="small" @click="openQuestions(record as ExamCampaign)">
                <FileTextOutlined /> 试题管理
              </a-button>
              <a-button type="link" size="small" @click="goCandidateStatus(record as ExamCampaign)">
                <EyeOutlined /> 考生状态
              </a-button>
              <a-popconfirm
                v-if="campaignState(record as ExamCampaign) === 'WAITING'"
                title="立即开考后将跳过原定开考时间，并按原限时重新计算结束时间，确认吗？"
                ok-text="立即开考"
                cancel-text="取消"
                @confirm="startNow(record as ExamCampaign)"
              >
                <a-button
                  type="link"
                  size="small"
                  :loading="startingId === String(record.id)"
                >
                  <PlayCircleOutlined /> 立即开考
                </a-button>
              </a-popconfirm>
              <a-popconfirm
                v-if="campaignState(record as ExamCampaign) === 'ENDED'"
                :title="isScoreQueryEnabled(record as ExamCampaign)
                  ? '关闭后考生将不能再查看本场成绩，确认关闭吗？'
                  : '开启后已报名考生可以查看本场成绩，确认开启吗？'"
                ok-text="确认"
                cancel-text="取消"
                @confirm="toggleScoreQuery(record as ExamCampaign)"
              >
                <a-button
                  type="link"
                  size="small"
                  :loading="scoreQueryTogglingId === String(record.id)"
                >
                  {{ isScoreQueryEnabled(record as ExamCampaign) ? '关闭查分' : '开启查分' }}
                </a-button>
              </a-popconfirm>
              <a-popconfirm
                :title="isCampaignEnabled(record as ExamCampaign)
                  ? '停用后考生将无法看到该批次，确认停用吗？'
                  : '启用后已分配的考生可以看到该批次，确认启用吗？'"
                ok-text="确认"
                cancel-text="取消"
                @confirm="toggleEnabled(record as ExamCampaign)"
              >
                <a-button
                  type="link"
                  size="small"
                  :loading="togglingId === String(record.id)"
                >
                  {{ isCampaignEnabled(record as ExamCampaign) ? '停用' : '启用' }}
                </a-button>
              </a-popconfirm>
            </div>
          </template>
        </a-table-column>
        <template #emptyText>
          <a-empty description="暂无考试场次，可点击右上角新建场次" />
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="editVisible"
      :title="modalTitle"
      :confirm-loading="saving"
      ok-text="保存"
      cancel-text="取消"
      @ok="saveCampaign"
    >
      <a-form layout="vertical">
        <a-form-item label="考试名称" required>
          <a-input
            v-model:value="form.name"
            :maxlength="200"
            show-count
            placeholder="例如：2026 智能体开发专题班"
          />
        </a-form-item>
        <a-form-item label="开考时间" required>
          <a-date-picker
            v-model:value="form.startAt"
            show-time
            format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="答题限时（分钟）" required>
          <a-input-number
            v-model:value="form.durationMinutes"
            :min="1"
            :max="10080"
            :precision="0"
            style="width: 100%"
            placeholder="请输入答题限时"
          />
        </a-form-item>
        <div class="calculated-time">
          <span>预计结束时间</span>
          <strong>{{ calculatedEndLabel }}</strong>
        </div>
        <a-form-item label="开放给考生">
          <div class="enabled-control">
            <a-switch v-model:checked="form.enabled" checked-children="启用" un-checked-children="停用" />
            <span>停用只隐藏该批次，不删除考生作品或成绩数据。</span>
          </div>
        </a-form-item>
      </a-form>
      <p class="modal-help">结束时间由"开考时间 + 答题限时"自动计算，考生端倒计时和自动收卷以此为准。</p>
    </a-modal>

    <!-- 试题管理弹窗 -->
    <a-modal
      v-model:open="questionVisible"
      :title="'试题管理 — ' + questionCampaignName"
      :footer="null"
      width="1200px"
      cancel-text="关闭"
    >
      <div style="margin-bottom: 12px; text-align: right">
        <a-button type="primary" size="small" @click="openCreateQuestion">
          <template #icon><PlusOutlined /></template>
          添加试题
        </a-button>
      </div>
      <a-table
        :data-source="questions"
        :loading="questionLoading"
        :pagination="false"
        row-key="id"
        size="small"
        :scroll="{ x: 1100 }"
      >
        <a-table-column title="序号" key="index" :width="60" fixed="left">
          <template #default="{ index }">{{ index + 1 }}</template>
        </a-table-column>
        <a-table-column title="题目" data-index="title" :width="280" fixed="left" />
        <a-table-column title="分值" data-index="score" :width="70" />
        <a-table-column title="建议用时" key="suggestedMinutes" :width="100">
          <template #default="{ record }">{{ record.suggestedMinutes != null ? record.suggestedMinutes + ' 分钟' : '—' }}</template>
        </a-table-column>
        <a-table-column title="任务场景" data-index="taskScenario" :width="220" show-overflow-tooltip />
        <a-table-column title="任务要求" data-index="taskRequirement" :width="220" show-overflow-tooltip />
        <a-table-column title="提交成果" data-index="submissionDeliverable" :width="220" show-overflow-tooltip />
        <a-table-column title="操作" key="action" :width="120" fixed="right">
          <template #default="{ record }">
            <a-button type="link" size="small" @click="openEditQuestion(record as ExamQuestion)">
              <EditOutlined /> 编辑
            </a-button>
            <a-popconfirm title="确认删除该试题？" ok-text="删除" cancel-text="取消" @confirm="removeQuestion(record as ExamQuestion)">
              <a-button type="link" size="small" danger>
                <DeleteOutlined />
              </a-button>
            </a-popconfirm>
          </template>
        </a-table-column>
        <template #emptyText>
          <a-empty description="暂无试题，点击上方「添加试题」" />
        </template>
      </a-table>
    </a-modal>

    <!-- 添加/编辑试题弹窗 -->
    <a-modal
      v-model:open="questionFormVisible"
      :title="editingQuestionId ? '编辑试题' : '添加试题'"
      :confirm-loading="questionSaving"
      ok-text="保存"
      cancel-text="取消"
      width="780px"
      @ok="saveQuestion"
    >
      <a-form layout="vertical">
        <a-form-item label="题目" required>
          <a-textarea v-model:value="questionForm.title" :rows="3" placeholder="请输入题目内容" />
        </a-form-item>
        <a-row :gutter="16">
          <a-col :span="8">
            <a-form-item label="分值" required>
              <a-input-number v-model:value="questionForm.score" :min="0" :max="1000" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="建议用时（分钟）">
              <a-input-number v-model:value="questionForm.suggestedMinutes" :min="0" :max="10080" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="排序">
              <a-input-number v-model:value="questionForm.sortOrder" :min="0" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="任务场景">
          <a-textarea v-model:value="questionForm.taskScenario" :rows="3" placeholder="描述该题目的任务场景" />
        </a-form-item>
        <a-form-item label="任务要求">
          <a-textarea v-model:value="questionForm.taskRequirement" :rows="3" placeholder="描述该题目的具体要求" />
        </a-form-item>
        <a-form-item label="提交成果">
          <a-textarea v-model:value="questionForm.submissionDeliverable" :rows="2" placeholder="说明考生需要提交的成果形式" />
        </a-form-item>
        <a-form-item label="附件">
          <template v-if="questionForm.attachmentUrl">
            <div class="attachment-item">
              <PaperClipOutlined />
              <span class="attachment-name">{{ questionForm.attachmentName }}</span>
              <span v-if="questionForm.attachmentSize" class="attachment-size">{{ formatFileSize(questionForm.attachmentSize) }}</span>
              <a-button type="link" size="small" danger @click="removeQuestionAttachment">删除</a-button>
            </div>
          </template>
          <template v-else>
            <a-upload :show-upload-list="false" :custom-request="handleQuestionFileUpload" accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.zip,.rar,.7z,.txt,.png,.jpg,.jpeg">
              <a-button :loading="questionFileUploading">
                <UploadOutlined /> 上传附件
              </a-button>
            </a-upload>
            <div class="upload-hint">支持 PDF、Word、Excel、PPT、压缩包、图片等，单文件 ≤ 20MB</div>
          </template>
        </a-form-item>
      </a-form>
    </a-modal>
  </main>
</template>

<style scoped lang="scss">
.attachment-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: #f6f8fa;
  border: 1px solid #e8edff;
  border-radius: 6px;
}
.attachment-name {
  color: #1d2a3f;
  font-weight: 500;
}
.attachment-size {
  color: #6d7c94;
  font-size: 12px;
}
.upload-hint {
  margin-top: 6px;
  color: #6d7c94;
  font-size: 12px;
}

.exam-admin-page {
  min-height: 100%;
  padding: 28px;
  color: #1d2a3f;
  background: #f5f8fc;
}

.page-heading,
.schedule-tip,
.campaign-card {
  max-width: 1180px;
  margin-right: auto;
  margin-left: auto;
}

.page-heading,
.campaign-card {
  border: 1px solid #dce7f5;
  background: #fff;
}

.page-heading {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 18px;
  padding: 22px 24px;
  border-radius: 8px;
}

.title-mark {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 52px;
  height: 52px;
  color: #fff;
  font-size: 25px;
  border-radius: 8px;
  background: #2f73d9;
}

.heading-copy {
  flex: 1;
}

.heading-copy > span {
  color: #2f73d9;
  font-size: 12px;
  font-weight: 700;
}

.heading-copy h1 {
  margin: 2px 0 3px;
  font-size: 23px;
}

.heading-copy p {
  margin: 0;
  color: #718096;
  font-size: 13px;
}

.heading-actions {
  display: flex;
  gap: 8px;
}

.schedule-tip {
  margin-bottom: 18px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  max-width: 1180px;
  gap: 14px;
  margin: 0 auto 18px;
}

.summary-card {
  border: 1px solid #dce7f5;
  border-left: 3px solid #2f73d9;
}

.summary-card.waiting { border-left-color: #2f73d9; }
.summary-card.open { border-left-color: #16a34a; }
.summary-card.ended { border-left-color: #8a98ad; }

.summary-card :deep(.ant-card-body) {
  padding: 16px 18px;
}

.summary-card span,
.summary-card small {
  display: block;
}

.summary-card span {
  color: #718096;
  font-size: 12px;
}

.summary-card strong {
  display: block;
  margin: 4px 0 2px;
  color: #1d2a3f;
  font-size: 25px;
  line-height: 1.15;
}

.summary-card small {
  color: #8a98ad;
  font-size: 12px;
}

.campaign-card {
  overflow: hidden;
  border-radius: 8px;
}

.campaign-name {
  color: #1d2a3f;
  font-weight: 600;
}

.campaign-id {
  margin-top: 3px;
  color: #8a98ad;
  font-size: 11px;
}

.muted,
.modal-help {
  color: #8a98ad;
}

.enabled-state {
  color: #16a34a;
  font-size: 12px;
}

.enabled-state.disabled {
  color: #8a98ad;
}

.action-links {
  display: flex;
  align-items: center;
  white-space: nowrap;
}

.action-links .ant-btn {
  padding-right: 6px;
  padding-left: 6px;
}

.calculated-time {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 42px;
  margin: -2px 0 18px;
  padding: 0 12px;
  color: #718096;
  font-size: 13px;
  border: 1px solid #e1e9f5;
  border-radius: 6px;
  background: #f8fbff;
}

.calculated-time strong {
  color: #1d2a3f;
  font-weight: 600;
}

.enabled-control {
  display: flex;
  align-items: center;
  gap: 10px;
}

.enabled-control span {
  color: #8a98ad;
  font-size: 12px;
}

.modal-help {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 760px) {
  .exam-admin-page {
    padding: 14px;
  }

  .page-heading {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .heading-actions {
    width: 100%;
  }

  .heading-actions .ant-btn {
    flex: 1;
  }

  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
