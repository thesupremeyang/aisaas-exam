<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import dayjs, { type Dayjs } from 'dayjs'
import { message, Modal } from 'ant-design-vue'
import {
  CalendarOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  EditOutlined,
  FileTextOutlined,
  PlusOutlined,
  ReloadOutlined,
  TeamOutlined,
  PlayCircleOutlined,
} from '@ant-design/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { useAccountStore } from '@/stores'
import { isPlatformAdminUsername } from '@/utils/platformAdmin'
import {
  createExamCampaign,
  listExamCampaignsForAdmin,
  updateExamCampaign,
  updateExamCampaignEnabled,
  updateExamCampaignScoreQuery,
  startExamNow,
  type ExamCampaign,
  type ExamCampaignUpdateRequest,
} from '@/api/exam'
import { RouteNames } from '@/router'
import {
  campaignState,
  campaignStateColor,
  campaignStateLabel,
  isCampaignEnabled,
  isScoreQueryEnabled,
  isPendingScorePublish,
} from '@/views/exam/campaignState'

interface CampaignForm {
  name: string
  startAt: Dayjs | null
  durationMinutes: number | null
  enabled: boolean
}

const router = useRouter()
const route = useRoute()
const accountStore = useAccountStore()
const loading = ref(false)
const rows = ref<ExamCampaign[]>([])
const nowMs = ref(Date.now())
const highlightId = ref<string | null>(null)
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

let clockTimer: number | undefined

const isPlatformAdmin = computed(
  () => isPlatformAdminUsername(accountStore.userInfo?.username),
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

function canEnableScoreQuery(campaign: ExamCampaign): boolean {
  return campaignState(campaign, nowMs.value) === 'ENDED' && !isScoreQueryEnabled(campaign)
}

function canDisableScoreQuery(campaign: ExamCampaign): boolean {
  return isScoreQueryEnabled(campaign)
}

const statusCounts = computed(() => ({
  waiting: rows.value.filter((item) => campaignState(item, nowMs.value) === 'WAITING').length,
  open: rows.value.filter((item) => campaignState(item, nowMs.value) === 'OPEN').length,
  ended: rows.value.filter((item) => campaignState(item, nowMs.value) === 'ENDED').length,
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
    // 从其他页面（如考生管理「时间设置」）带 campaignId 跳入时，短暂高亮对应行
    const requested = route.query.campaignId ? String(route.query.campaignId) : ''
    if (requested && rows.value.some((item) => String(item.id) === requested)) {
      highlightId.value = requested
      window.setTimeout(() => { highlightId.value = null }, 3000)
    }
  } catch {
    message.warning('加载考试批次失败')
  } finally {
    loading.value = false
  }
}

function rowClassName(record: ExamCampaign): string {
  return highlightId.value && String(record.id) === highlightId.value ? 'row-highlight' : ''
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
    if (isCreating.value && updated?.id) {
      // 新建成功后立即引导下一步：添加试题（考试没有试题无法开考）
      Modal.confirm({
        title: `「${name}」已创建`,
        content: '考试还需要配置试题才能开考，是否立即添加试题？',
        okText: '立即添加试题',
        cancelText: '稍后再说',
        onOk: () => {
          void router.push({
            name: RouteNames.EXAM_QUESTIONS,
            query: { campaignId: String(updated.id) },
          })
        },
      })
    } else {
      message.success('考试批次已保存')
    }
  } catch {
    message.warning(isCreating.value ? '创建考试批次失败' : '保存考试批次失败')
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
    message.warning(nextEnabled ? '启用考试批次失败' : '停用考试批次失败')
  } finally {
    togglingId.value = null
  }
}

/** 操作列"更多"下拉的确认弹窗（原内联 popconfirm 迁移至此） */
function confirmStartNow(campaign: ExamCampaign) {
  Modal.confirm({
    title: '立即开考？',
    content: '将跳过原定开考时间，并按原限时重新计算结束时间。',
    okText: '立即开考',
    cancelText: '取消',
    onOk: () => startNow(campaign),
  })
}

function confirmEnableScoreQuery(campaign: ExamCampaign) {
  Modal.confirm({
    title: '开启查分？',
    content: '开启后已报名考生可以查看本场成绩。',
    okText: '开启查分',
    cancelText: '取消',
    onOk: () => enableScoreQuery(campaign),
  })
}

function confirmDisableScoreQuery(campaign: ExamCampaign) {
  Modal.confirm({
    title: '关闭查分？',
    content: '关闭后考生将不能再查看本场成绩。',
    okText: '关闭查分',
    cancelText: '取消',
    onOk: () => disableScoreQuery(campaign),
  })
}

function confirmToggleEnabled(campaign: ExamCampaign) {
  const disabling = isCampaignEnabled(campaign)
  Modal.confirm({
    title: disabling ? '停用场次？' : '启用场次？',
    content: disabling ? '停用后考生将无法看到该批次。' : '启用后已分配的考生可以看到该批次。',
    okText: disabling ? '停用' : '启用',
    cancelText: '取消',
    onOk: () => toggleEnabled(campaign),
  })
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
    message.warning('立即开考失败，请先确认场次尚未结束')
  } finally {
    startingId.value = null
  }
}

async function setScoreQueryEnabled(campaign: ExamCampaign, nextEnabled: boolean) {
  if (!campaign.id) return
  if (nextEnabled && !canEnableScoreQuery(campaign)) return
  if (!nextEnabled && !canDisableScoreQuery(campaign)) return
  scoreQueryTogglingId.value = String(campaign.id)
  try {
    const response = await updateExamCampaignScoreQuery(campaign.id, nextEnabled)
    replaceRow(response.data.data)
    message.success(nextEnabled ? '查分模式已开启' : '查分模式已关闭')
  } catch {
    message.warning(nextEnabled ? '开启查分模式失败' : '关闭查分模式失败')
  } finally {
    scoreQueryTogglingId.value = null
  }
}

function enableScoreQuery(campaign: ExamCampaign) {
  void setScoreQueryEnabled(campaign, true)
}

function disableScoreQuery(campaign: ExamCampaign) {
  void setScoreQueryEnabled(campaign, false)
}

function goCandidateStatus(campaign: ExamCampaign) {
  if (!campaign.id) return
  void router.push({
    name: RouteNames.EXAM_CANDIDATE_STATUS,
    query: { campaignId: String(campaign.id) },
  })
}

function goQuestions(campaign: ExamCampaign) {
  if (!campaign.id) return
  void router.push({
    name: RouteNames.EXAM_QUESTIONS,
    query: { campaignId: String(campaign.id) },
  })
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
        :row-class-name="rowClassName"
        :scroll="{ x: 1240 }"
      >
        <a-table-column title="考试名称" data-index="name" key="name" :width="240">
          <template #default="{ record }">
            <div class="campaign-name">{{ record.name || '未命名考试' }}</div>
            <div class="campaign-id">批次 ID：{{ record.id || '—' }}</div>
          </template>
        </a-table-column>
        <a-table-column title="状态" key="status" :width="110">
          <template #default="{ record }">
            <a-tag :color="campaignStateColor(campaignState(record as ExamCampaign, nowMs))">
              {{ campaignStateLabel(campaignState(record as ExamCampaign, nowMs)) }}
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
        <a-table-column title="查分模式" key="scoreQuery" :width="130">
          <template #default="{ record }">
            <a-tag v-if="isScoreQueryEnabled(record as ExamCampaign)" color="green">已开启</a-tag>
            <a-tag v-else-if="isPendingScorePublish(record as ExamCampaign, nowMs)" color="orange">待发布成绩</a-tag>
            <span v-else class="muted">未开启</span>
          </template>
        </a-table-column>
        <a-table-column title="操作" key="action" :width="250">
          <template #default="{ record }">
            <div class="action-links">
              <a-button type="link" size="small" @click="openEdit(record as ExamCampaign)">
                <EditOutlined /> 编辑
              </a-button>
              <a-button type="link" size="small" @click="goQuestions(record as ExamCampaign)">
                <FileTextOutlined /> 试题
              </a-button>
              <a-button type="link" size="small" @click="goCandidateStatus(record as ExamCampaign)">
                <TeamOutlined /> 考生
              </a-button>
              <a-dropdown>
                <a-button type="link" size="small">
                  更多 <DownOutlined />
                </a-button>
                <template #overlay>
                  <a-menu>
                    <a-menu-item
                      v-if="campaignState(record as ExamCampaign, nowMs) === 'WAITING'"
                      key="startNow"
                      @click="confirmStartNow(record as ExamCampaign)"
                    >
                      立即开考
                    </a-menu-item>
                    <a-menu-item
                      v-if="canEnableScoreQuery(record as ExamCampaign)"
                      key="enableScoreQuery"
                      @click="confirmEnableScoreQuery(record as ExamCampaign)"
                    >
                      开启查分
                    </a-menu-item>
                    <a-menu-item
                      v-if="canDisableScoreQuery(record as ExamCampaign)"
                      key="disableScoreQuery"
                      danger
                      @click="confirmDisableScoreQuery(record as ExamCampaign)"
                    >
                      关闭查分
                    </a-menu-item>
                    <a-menu-item
                      key="toggleEnabled"
                      :danger="isCampaignEnabled(record as ExamCampaign)"
                      @click="confirmToggleEnabled(record as ExamCampaign)"
                    >
                      {{ isCampaignEnabled(record as ExamCampaign) ? '停用' : '启用' }}
                    </a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
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
  </main>
</template>

<style scoped lang="scss">
.exam-admin-page {
  min-height: 100%;
  padding: 28px;
  color: #1d2a3f;
  background: #f5f8fc;
}

:deep(.row-highlight > td) {
  animation: row-flash 1.2s ease-in-out 2;
}

@keyframes row-flash {
  0%,
  100% {
    background: transparent;
  }
  50% {
    background: #e6f1fb;
  }
}

.page-heading,
.schedule-tip,
.campaign-card {
  max-width: 1560px;
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
  max-width: 1560px;
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
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 0;
  white-space: normal;
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
  .action-links {
    flex-direction: column;
    align-items: stretch;
  }
  .action-links .ant-btn {
    width: 100%;
    justify-content: flex-start;
    padding: 6px 4px;
    min-height: 40px;
  }
  .ant-table {
    overflow-x: auto;
  }
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
