<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import { message } from 'ant-design-vue'
import {
  CalendarOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  LoginOutlined,
  ReloadOutlined,
  TeamOutlined,
} from '@ant-design/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { useAccountStore } from '@/stores'
import {
  getExamCandidateStatus,
  listExamCampaignsForAdmin,
  type ExamCandidateStatus,
  type ExamCampaign,
  type ExamCandidateStatusSnapshot,
} from '@/api/exam'
import { RouteNames } from '@/router'

const router = useRouter()
const route = useRoute()
const accountStore = useAccountStore()
const campaigns = ref<ExamCampaign[]>([])
const selectedCampaignId = ref<string | number>()
const snapshot = ref<ExamCandidateStatusSnapshot | null>(null)
const loading = ref(false)
const campaignsLoading = ref(false)
const nowMs = ref(Date.now())
const clockOffsetMs = ref(0)
const lastUpdated = ref('')

let pollTimer: number | undefined
let tickTimer: number | undefined

const isPlatformAdmin = computed(
  () => accountStore.userInfo?.username?.toLowerCase() === 'admin',
)
const rows = computed(() => snapshot.value?.candidates || [])
const serverNowMs = computed(() => nowMs.value + clockOffsetMs.value)

function parseTime(value?: string): number | null {
  if (!value) return null
  const normalized = value.includes(' ') && !value.includes('T') ? value.replace(' ', 'T') : value
  const parsed = dayjs(normalized).valueOf()
  return Number.isNaN(parsed) ? null : parsed
}

function formatTime(value?: string): string {
  const timestamp = parseTime(value)
  return timestamp === null ? '未设置' : dayjs(timestamp).format('YYYY-MM-DD HH:mm:ss')
}

function formatDuration(seconds?: number | null): string {
  if (seconds === null || seconds === undefined) return '—'
  const value = Math.max(0, Math.floor(seconds))
  const hours = Math.floor(value / 3600)
  const minutes = Math.floor((value % 3600) / 60)
  const rest = value % 60
  return [hours, minutes, rest].map((item) => String(item).padStart(2, '0')).join(':')
}

function stateLabel(state?: string): string {
  return ({ WAITING: '待开考', OPEN: '考试中', ENDED: '已结束' }[state || ''] || '未设置')
}

function stateColor(state?: string): string {
  return ({ WAITING: 'blue', OPEN: 'green', ENDED: 'default' }[state || ''] || 'default')
}

function submissionLabel(status?: string): string {
  return ({
    DRAFT: '未交卷',
    AI_QUEUED: '已交卷 · AI排队',
    REVIEW_PENDING: '待评审',
    PUBLISHED: '已发布',
    TIMEOUT_SUBMITTED: '超时自动交卷',
  }[status || ''] || status || '未交卷')
}

function submissionColor(status?: string): string {
  return ({
    DRAFT: 'processing',
    AI_QUEUED: 'blue',
    REVIEW_PENDING: 'gold',
    PUBLISHED: 'green',
    TIMEOUT_SUBMITTED: 'orange',
  }[status || ''] || 'default')
}

function liveElapsed(row: ExamCandidateStatus): number {
  if (!row.entered || snapshot.value?.examState === 'WAITING') return 0
  const start = parseTime(snapshot.value?.examStartAt)
  const end = parseTime(snapshot.value?.examEndAt)
  if (start === null) return 0
  const target = snapshot.value?.examState === 'ENDED' && end !== null ? end : serverNowMs.value
  return Math.max(0, Math.floor((target - start) / 1000))
}

function liveRemaining(): number {
  const state = snapshot.value?.examState
  const target = state === 'WAITING'
    ? parseTime(snapshot.value?.examStartAt)
    : parseTime(snapshot.value?.examEndAt)
  if (target === null || target === undefined || state === 'ENDED') return 0
  return Math.max(0, Math.ceil((target - serverNowMs.value) / 1000))
}

function elapsedText(row: ExamCandidateStatus): string {
  if (!row.entered) return '未进入'
  if (snapshot.value?.examState === 'WAITING') return '待开考'
  return formatDuration(liveElapsed(row))
}

function remainingText(): string {
  if (snapshot.value?.examState === 'WAITING') return `距开考 ${formatDuration(liveRemaining())}`
  if (snapshot.value?.examState === 'ENDED') return '已结束'
  return formatDuration(liveRemaining())
}

async function loadCampaigns() {
  if (!isPlatformAdmin.value) {
    await router.replace({ name: RouteNames.EXPLORE })
    return
  }
  campaignsLoading.value = true
  try {
    const response = await listExamCampaignsForAdmin()
    campaigns.value = response.data.data || []
    const requestedCampaignId = route.query.campaignId
    const requestedCampaign = campaigns.value.find(
      (item) => String(item.id) === String(requestedCampaignId || ''),
    )
    const selectedExists = campaigns.value.some((item) => String(item.id) === String(selectedCampaignId.value))
    if (requestedCampaign) selectedCampaignId.value = requestedCampaign.id
    else if (!selectedExists) selectedCampaignId.value = campaigns.value[0]?.id as string | undefined
    await loadStatus()
  } catch {
    message.error('加载考试批次失败')
  } finally {
    campaignsLoading.value = false
  }
}

async function loadStatus() {
  if (!selectedCampaignId.value || !isPlatformAdmin.value) return
  loading.value = true
  try {
    const response = await getExamCandidateStatus(selectedCampaignId.value)
    snapshot.value = response.data.data || null
    const serverTime = parseTime(snapshot.value?.serverNow)
    if (serverTime !== null) clockOffsetMs.value = serverTime - Date.now()
    lastUpdated.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  } catch {
    message.error('加载考生状态失败')
  } finally {
    loading.value = false
  }
}

function handleCampaignChange() {
  void loadStatus()
}

function goSchedule() {
  void router.push({ name: RouteNames.EXAM_ADMIN })
}

onMounted(async () => {
  await loadCampaigns()
  tickTimer = window.setInterval(() => {
    nowMs.value = Date.now()
  }, 1000)
  pollTimer = window.setInterval(() => void loadStatus(), 5000)
})

onBeforeUnmount(() => {
  if (tickTimer) window.clearInterval(tickTimer)
  if (pollTimer) window.clearInterval(pollTimer)
})
</script>

<template>
  <main class="candidate-status-page">
    <header class="page-heading">
      <div class="title-mark"><TeamOutlined /></div>
      <div class="heading-copy">
        <span>考试管理</span>
        <h1>考生状态管理</h1>
        <p>实时查看当前考试批次的登录、入场和时间进度。</p>
      </div>
      <div class="heading-actions">
        <a-button @click="goSchedule">
          <template #icon><CalendarOutlined /></template>
          时间设置
        </a-button>
        <a-button :loading="loading || campaignsLoading" @click="loadCampaigns">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
      </div>
    </header>

    <section class="toolbar-card">
      <div class="toolbar-label">选择考试批次</div>
      <a-select
        v-model:value="selectedCampaignId"
        :loading="campaignsLoading"
        style="width: min(460px, 100%)"
        placeholder="请选择考试批次"
        @change="handleCampaignChange"
      >
        <a-select-option v-for="item in campaigns" :key="item.id" :value="item.id">
          {{ item.name }}
        </a-select-option>
      </a-select>
      <div v-if="snapshot" class="window-summary">
        <a-tag :color="stateColor(snapshot.examState)">{{ stateLabel(snapshot.examState) }}</a-tag>
        <span>{{ formatTime(snapshot.examStartAt) }} — {{ formatTime(snapshot.examEndAt) }}</span>
        <span v-if="lastUpdated" class="muted">更新于 {{ lastUpdated }} · 每5秒自动刷新</span>
      </div>
    </section>

    <a-alert
      v-if="snapshot && !snapshot.loginStatusAvailable"
      type="warning"
      show-icon
      message="当前无法读取登录会话"
      description="考试进入和时间数据仍可查看，登录状态将在缓存服务恢复后自动更新。"
      class="status-alert"
    />

    <section v-if="snapshot" class="stats-grid">
      <a-card :bordered="false" class="stat-card">
        <div class="stat-icon total"><TeamOutlined /></div>
        <div><span>考生账号</span><strong>{{ snapshot.totalCandidates ?? 0 }}</strong></div>
      </a-card>
      <a-card :bordered="false" class="stat-card">
        <div class="stat-icon online"><LoginOutlined /></div>
        <div><span>当前已登录</span><strong>{{ snapshot.loginStatusAvailable ? snapshot.loggedInCount : '—' }}</strong></div>
      </a-card>
      <a-card :bordered="false" class="stat-card">
        <div class="stat-icon entered"><CheckCircleOutlined /></div>
        <div><span>已进入考试</span><strong>{{ snapshot.enteredCount ?? 0 }}</strong></div>
      </a-card>
      <a-card :bordered="false" class="stat-card">
        <div class="stat-icon submitted"><ClockCircleOutlined /></div>
        <div><span>已交卷</span><strong>{{ snapshot.submittedCount ?? 0 }}</strong></div>
      </a-card>
    </section>

    <a-card v-if="snapshot" :bordered="false" class="candidate-card">
      <a-table
        :data-source="rows"
        :loading="loading"
        :pagination="false"
        row-key="enrollmentId"
        :scroll="{ x: 1120 }"
      >
        <a-table-column title="考生" key="candidate" :width="190" fixed="left">
          <template #default="{ record }">
            <div class="candidate-name">{{ record.candidateName || '未命名考生' }}</div>
            <div class="candidate-account">{{ record.username || '—' }}</div>
          </template>
        </a-table-column>
        <a-table-column title="组织" data-index="organization" key="organization" :width="150">
          <template #default="{ record }">{{ record.organization || '—' }}</template>
        </a-table-column>
        <a-table-column title="登录状态" key="loggedIn" :width="120">
          <template #default="{ record }">
            <a-tag v-if="!snapshot.loginStatusAvailable" color="default">未知</a-tag>
            <a-tag v-else :color="record.loggedIn ? 'green' : 'default'">
              {{ record.loggedIn ? '已登录' : '未登录' }}
            </a-tag>
          </template>
        </a-table-column>
        <a-table-column title="考试状态" key="entered" :width="130">
          <template #default="{ record }">
            <a-tag :color="record.entered ? 'green' : 'default'">
              {{ record.entered ? '已进入' : '未进入' }}
            </a-tag>
          </template>
        </a-table-column>
        <a-table-column title="进入时间" key="enteredAt" :width="180">
          <template #default="{ record }">{{ formatTime(record.enteredAt) }}</template>
        </a-table-column>
        <a-table-column title="已开考时长" key="elapsed" :width="150">
          <template #default="{ record }">
            <span class="duration-value">{{ elapsedText(record) }}</span>
          </template>
        </a-table-column>
        <a-table-column title="剩余时间" key="remaining" :width="150">
          <template #default>
            <span :class="{ 'remaining-urgent': snapshot.examState === 'OPEN' && liveRemaining() <= 1800 }">
              {{ remainingText() }}
            </span>
          </template>
        </a-table-column>
        <a-table-column title="提交状态" key="submissionStatus" :width="150">
          <template #default="{ record }">
            <a-tag :color="submissionColor(record.submissionStatus)">{{ submissionLabel(record.submissionStatus) }}</a-tag>
          </template>
        </a-table-column>
        <template #emptyText>
          <a-empty description="当前批次暂无考生账号" />
        </template>
      </a-table>
    </a-card>

    <a-empty v-else-if="!campaignsLoading" description="请选择一个考试批次" class="empty-state" />
  </main>
</template>

<style scoped lang="scss">
.candidate-status-page {
  min-height: 100%;
  padding: 28px;
  color: #1d2a3f;
  background: #f5f8fc;
}

.page-heading,
.toolbar-card,
.candidate-card {
  max-width: 1180px;
  margin-right: auto;
  margin-left: auto;
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
  background: #16a34a;
}

.heading-copy {
  flex: 1;
}

.heading-copy > span {
  color: #16a34a;
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

.toolbar-card {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  margin-bottom: 18px;
  padding: 16px 20px;
  border-radius: 8px;
}

.toolbar-label {
  color: #52627a;
  font-size: 13px;
  font-weight: 600;
}

.window-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #52627a;
  font-size: 12px;
}

.muted,
.candidate-account {
  color: #8a98ad;
}

.status-alert,
.candidate-card {
  max-width: 1180px;
  margin-right: auto;
  margin-bottom: 18px;
  margin-left: auto;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  max-width: 1180px;
  gap: 14px;
  margin: 0 auto 18px;
}

.stat-card :deep(.ant-card-body) {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 17px 18px;
}

.stat-icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 9px;
  font-size: 18px;
}

.stat-icon.total { color: #2563eb; background: #eaf2ff; }
.stat-icon.online { color: #16a34a; background: #eaf8ee; }
.stat-icon.entered { color: #7c3aed; background: #f1eaff; }
.stat-icon.submitted { color: #d97706; background: #fff4df; }

.stat-card span {
  display: block;
  color: #718096;
  font-size: 12px;
}

.stat-card strong {
  display: block;
  margin-top: 2px;
  color: #1d2a3f;
  font-size: 22px;
  line-height: 1.2;
}

.candidate-card {
  overflow: hidden;
  border-radius: 8px;
}

.candidate-name {
  color: #1d2a3f;
  font-weight: 600;
}

.candidate-account {
  margin-top: 3px;
  font-size: 12px;
}

.duration-value,
.remaining-urgent {
  font-variant-numeric: tabular-nums;
}

.remaining-urgent {
  color: #d46b08;
  font-weight: 700;
}

.empty-state {
  margin-top: 80px;
}

@media (max-width: 760px) {
  .candidate-status-page {
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

  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .window-summary {
    width: 100%;
    flex-wrap: wrap;
  }
}
</style>
