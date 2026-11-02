<!--
  答卷管理（平台 admin 专用）

  按考试批次查看考生答卷：主表按考生分组（姓名 + 考号），
  展开某位考生可一次性查看其全部题目的答卷明细；
  通过「查看详细配置」进入考生当时的智能体配置界面（模拟态，全站隔离），
  支持进入对话（复用平台 Chat 页，AG-UI 流式）。

  @author workbuddy
-->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import { message } from 'ant-design-vue'
import {
  AppstoreOutlined,
  DownOutlined,
  FileSearchOutlined,
  MessageOutlined,
  ReloadOutlined,
  SearchOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { useAccountStore } from '@/stores'
import { isPlatformAdminUsername } from '@/utils/platformAdmin'
import {
  listExamCampaignsForAdmin,
  listExamSubmissionsForAdmin,
  type ExamCampaign,
  type ExamSubmissionAdminRow,
} from '@/api/exam'
import { RouteNames } from '@/router'
import {
  campaignState,
  campaignStateColor,
  campaignStateLabel,
  parseExamTime,
} from '@/views/exam/campaignState'

const router = useRouter()
const route = useRoute()
const accountStore = useAccountStore()

const campaigns = ref<ExamCampaign[]>([])
const selectedCampaignId = ref<string>()
const campaignsLoading = ref(false)
const rows = ref<ExamSubmissionAdminRow[]>([])
const rowsLoading = ref(false)
const searchKeyword = ref('')
/** 展开的考生行（enrollmentId）；加载后默认展开第一位考生 */
const expandedRowKeys = ref<string[]>([])

const isPlatformAdmin = computed(
  () => isPlatformAdminUsername(accountStore.userInfo?.username),
)
const selectedCampaign = computed(() =>
  campaigns.value.find((item) => String(item.id) === String(selectedCampaignId.value)),
)
const selectedState = computed(() =>
  selectedCampaign.value ? campaignState(selectedCampaign.value, Date.now()) : null,
)
const timeWindowLabel = computed(() => {
  const start = parseExamTime(selectedCampaign.value?.submissionStart)
  const end = parseExamTime(selectedCampaign.value?.submissionEnd)
  if (!start && !end) return '时间未设置'
  const fmt = (v: dayjs.Dayjs | null) => (v ? v.format('YYYY-MM-DD HH:mm') : '—')
  return `${fmt(start)} ~ ${fmt(end)}`
})

/** 按考生聚合答卷行 */
interface CandidateGroup {
  enrollmentId: string
  candidateName: string
  candidateUsername: string
  organization: string
  submissions: ExamSubmissionAdminRow[]
  submittedCount: number
  agentCount: number
  lastSubmittedAt: string
}

function isSubmitted(row: ExamSubmissionAdminRow): boolean {
  return !!row.status && row.status !== 'DRAFT'
}

const candidateGroups = computed<CandidateGroup[]>(() => {
  const groups = new Map<string, CandidateGroup>()
  for (const row of rows.value) {
    const key = String(row.enrollmentId ?? row.submissionId)
    let group = groups.get(key)
    if (!group) {
      group = {
        enrollmentId: key,
        candidateName: row.candidateName || '未命名考生',
        candidateUsername: row.candidateUsername || '—',
        organization: row.organization || '—',
        submissions: [],
        submittedCount: 0,
        agentCount: 0,
        lastSubmittedAt: '',
      }
      groups.set(key, group)
    }
    group.submissions.push(row)
    if (isSubmitted(row)) {
      group.submittedCount += 1
      if (row.submittedAt && row.submittedAt > group.lastSubmittedAt) {
        group.lastSubmittedAt = row.submittedAt
      }
    }
    if (row.agentId) group.agentCount += 1
  }
  return Array.from(groups.values()).sort((a, b) =>
    (b.lastSubmittedAt || '').localeCompare(a.lastSubmittedAt || ''),
  )
})

const filteredGroups = computed<CandidateGroup[]>(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  if (!keyword) return candidateGroups.value
  return candidateGroups.value.filter((group) =>
    [
      group.candidateName,
      group.candidateUsername,
      group.organization,
      ...group.submissions.flatMap((row) => [row.questionTitle, row.agentName]),
    ].some((value) => value?.toLowerCase().includes(keyword)),
  )
})

const stats = computed(() => {
  const groups = filteredGroups.value
  const fullySubmitted = groups.filter(
    (group) => group.submittedCount > 0 && group.submittedCount === group.submissions.length,
  ).length
  return {
    candidates: groups.length,
    fullySubmitted,
    submissions: groups.reduce((sum, group) => sum + group.submissions.length, 0),
    withAgent: groups.reduce(
      (sum, group) => sum + group.submissions.filter((row) => row.agentId).length,
      0,
    ),
  }
})

function statusLabel(status?: string): string {
  return ({
    DRAFT: '未交卷',
    AI_QUEUED: '已交卷 · AI排队',
    REVIEW_PENDING: '待评审',
    PUBLISHED: '已发布',
    TIMEOUT_SUBMITTED: '超时自动交卷',
    PARTIAL_TIMEOUT_SUBMITTED: '超时交卷（内容不完整）',
    NO_ANSWER: '超时交卷（未作答）',
  }[status || ''] || status || '未交卷')
}

function statusColor(status?: string): string {
  return ({
    DRAFT: 'processing',
    AI_QUEUED: 'blue',
    REVIEW_PENDING: 'gold',
    PUBLISHED: 'green',
    TIMEOUT_SUBMITTED: 'orange',
    PARTIAL_TIMEOUT_SUBMITTED: 'volcano',
    NO_ANSWER: 'red',
  }[status || ''] || 'default')
}

function formatTime(value?: string): string {
  if (!value) return '—'
  const parsed = dayjs(value.includes(' ') && !value.includes('T') ? value.replace(' ', 'T') : value)
  return parsed.isValid() ? parsed.format('MM-DD HH:mm:ss') : '—'
}

async function loadCampaigns() {
  if (!isPlatformAdmin.value) {
    await router.replace({ name: RouteNames.EXPLORE })
    return
  }
  campaignsLoading.value = true
  try {
    const res = await listExamCampaignsForAdmin()
    campaigns.value = res.data.data || []
    const requested = campaigns.value.find(
      (item) => String(item.id) === String(route.query.campaignId || ''),
    )
    if (requested) {
      selectedCampaignId.value = String(requested.id)
    } else if (!campaigns.value.some((item) => String(item.id) === String(selectedCampaignId.value))) {
      selectedCampaignId.value = campaigns.value[0] ? String(campaigns.value[0].id) : undefined
    }
    await loadRows()
  } catch {
    message.warning('加载考试批次失败')
  } finally {
    campaignsLoading.value = false
  }
}

async function loadRows() {
  if (!selectedCampaignId.value) {
    rows.value = []
    return
  }
  rowsLoading.value = true
  try {
    const res = await listExamSubmissionsForAdmin(selectedCampaignId.value)
    rows.value = res.data.data || []
    // 默认展开第一位考生，让答卷明细开箱可见
    const first = candidateGroups.value[0]
    expandedRowKeys.value = first ? [first.enrollmentId] : []
  } catch {
    message.warning('加载答卷失败')
  } finally {
    rowsLoading.value = false
  }
}

function handleCampaignChange() {
  void loadRows()
}

/** 进入考生配置界面：切换到考生租户上下文（模拟态），智能体/Skill/知识库等全部隔离为该考生数据。
 *  路由完成后整页刷新，确保 WebSocket/页面状态都以考生租户身份重建。 */
async function openAgentViewPage(group: CandidateGroup) {
  if (!group.agentCount) return
  const tenantId = group.submissions.find((s) => s.tenantId != null)?.tenantId
  if (tenantId == null) {
    message.warning('该考生答卷缺少租户信息，无法进入')
    return
  }
  try {
    await accountStore.enterTenantView(tenantId, group.candidateName || '考生')
    await router.push('/agent')
    window.setTimeout(() => window.location.reload(), 100)
  } catch (error) {
    console.error(error)
    message.error('进入考生配置界面失败')
  }
}

function openChat(row: ExamSubmissionAdminRow) {
  if (!row.agentId) return
  const base = import.meta.env.BASE_URL || '/'
  const url = `${window.location.origin}${base}#/chat/${row.agentId}`
  window.open(url, '_blank')
}

function yesNo(value?: boolean): string {
  if (value === true) return '开启'
  if (value === false) return '关闭'
  return '—'
}

onMounted(loadCampaigns)
</script>

<template>
  <main class="submission-admin-page">
    <header class="page-heading">
      <div class="title-mark"><UnorderedListOutlined /></div>
      <div class="heading-copy">
        <span>考试管理</span>
        <h1>答卷管理</h1>
        <p>按考生查看答卷与提交的智能体：展开考生即可查看其全部题目答卷，可查看智能体配置并直接对话。</p>
      </div>
      <div class="heading-actions">
        <a-button :loading="rowsLoading" :disabled="!selectedCampaignId" @click="loadRows">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
      </div>
    </header>

    <section class="toolbar-card">
      <div class="toolbar-label">选择考试批次</div>
      <div class="toolbar-line">
        <a-select
          v-model:value="selectedCampaignId"
          :loading="campaignsLoading"
          style="width: min(420px, 100%)"
          placeholder="请选择考试批次"
          @change="handleCampaignChange"
        >
          <a-select-option v-for="item in campaigns" :key="item.id" :value="String(item.id)">
            {{ item.name || '未命名考试' }}
          </a-select-option>
        </a-select>
        <a-input
          v-model:value="searchKeyword"
          allow-clear
          placeholder="搜索考生姓名、考号、组织、题目或智能体"
          style="width: min(300px, 100%)"
        >
          <template #prefix><SearchOutlined /></template>
        </a-input>
      </div>
      <div v-if="selectedCampaign" class="window-summary">
        <a-tag :color="selectedState ? campaignStateColor(selectedState) : 'default'">
          {{ selectedState ? campaignStateLabel(selectedState) : '—' }}
        </a-tag>
        <span>{{ timeWindowLabel }}</span>
      </div>
    </section>

    <section v-if="selectedCampaignId" class="stats-grid">
      <a-card :bordered="false" class="stat-card">
        <div><span>考生数</span><strong>{{ stats.candidates }}</strong></div>
      </a-card>
      <a-card :bordered="false" class="stat-card">
        <div><span>已交全卷考生</span><strong>{{ stats.fullySubmitted }}</strong></div>
      </a-card>
      <a-card :bordered="false" class="stat-card">
        <div><span>答卷总数</span><strong>{{ stats.submissions }}</strong></div>
      </a-card>
      <a-card :bordered="false" class="stat-card">
        <div><span>含智能体答卷</span><strong>{{ stats.withAgent }}</strong></div>
      </a-card>
    </section>

    <a-card v-if="selectedCampaignId" :bordered="false" class="submission-card">
      <div class="expand-hint">
        <DownOutlined />
        点击考生行任意位置即可展开 / 收起该考生的全部题目答卷；展开后可查看智能体配置详情或进入对话。
      </div>
      <a-table
        :data-source="filteredGroups"
        :loading="rowsLoading"
        :pagination="{ pageSize: 20, showTotal: (total: number) => `共 ${total} 名考生` }"
        row-key="enrollmentId"
        v-model:expandedRowKeys="expandedRowKeys"
        expand-row-by-click
      >
        <template #expandedRowRender="{ record }">
          <div class="detail-wrap" @click.stop>
            <table class="detail-table">
              <thead>
                <tr>
                  <th style="width: 34%">题目</th>
                  <th style="width: 26%">智能体作品</th>
                  <th style="width: 16%">答卷状态</th>
                  <th style="width: 14%">提交时间</th>
                  <th style="width: 10%">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in record.submissions" :key="row.submissionId">
                  <td>
                    <div class="question-cell">{{ row.questionTitle || '—' }}</div>
                    <div v-if="row.questionScore != null" class="question-score">{{ row.questionScore }} 分</div>
                  </td>
                  <td>
                    <template v-if="row.agentId">
                      <div class="agent-name">{{ row.agentName || `#${row.agentId}` }}</div>
                    </template>
                    <span v-else class="muted">未关联智能体</span>
                  </td>
                  <td><a-tag :color="statusColor(row.status)">{{ statusLabel(row.status) }}</a-tag></td>
                  <td>{{ formatTime(row.submittedAt) }}</td>
                  <td>
                    <a-button v-if="row.agentId" type="link" size="small" @click="openChat(row)">
                      <template #icon><MessageOutlined /></template>
                      对话
                    </a-button>
                    <span v-else class="muted">—</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
        <a-table-column title="考生" key="candidate" :width="240">
          <template #default="{ record }">
            <div class="candidate-name">{{ record.candidateName }}</div>
            <div class="candidate-username">考号：{{ record.candidateUsername }}</div>
            <a-button
              v-if="record.agentCount > 0"
              type="primary"
              size="small"
              class="enter-view-btn"
              @click.stop="openAgentViewPage(record)"
            >
              <template #icon><FileSearchOutlined /></template>
              查看详细配置
            </a-button>
          </template>
        </a-table-column>
        <a-table-column title="组织/班级" data-index="organization" key="organization" :width="160" />
        <a-table-column title="答卷进度" key="progress" :width="140">
          <template #default="{ record }">
            <span :class="['progress-value', { done: record.submittedCount === record.submissions.length }]">
              已交 {{ record.submittedCount }} / {{ record.submissions.length }} 题
            </span>
          </template>
        </a-table-column>
        <a-table-column title="智能体作品" key="agentCount" :width="130">
          <template #default="{ record }">{{ record.agentCount }} 份</template>
        </a-table-column>
        <a-table-column title="最近提交" key="lastSubmittedAt" :width="160">
          <template #default="{ record }">{{ formatTime(record.lastSubmittedAt) }}</template>
        </a-table-column>
        <template #emptyText>
          <a-empty description="该批次暂无考生答卷" />
        </template>
      </a-table>
    </a-card>

    <a-empty v-else-if="!campaignsLoading" description="请选择一个考试批次" class="empty-state" />
  </main>
</template>

<style scoped lang="scss">
.submission-admin-page {
  min-height: 100%;
  padding: 28px;
  color: #1d2a3f;
  background: #f5f8fc;
}

.page-heading,
.toolbar-card,
.submission-card {
  max-width: 1560px;
  margin-right: auto;
  margin-left: auto;
}

.page-heading,
.toolbar-card,
.submission-card {
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
  background: #7c3aed;
}

.heading-copy {
  flex: 1;
}

.heading-copy > span {
  color: #7c3aed;
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

.toolbar-card {
  margin-bottom: 18px;
  padding: 18px 24px;
  border-radius: 8px;
}

.toolbar-label {
  margin-bottom: 8px;
  color: #718096;
  font-size: 12px;
}

.toolbar-line {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.window-summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
  color: #1d2a3f;
  font-size: 13px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  max-width: 1560px;
  gap: 14px;
  margin: 0 auto 18px;
}

.stat-card {
  border: 1px solid #dce7f5;
  border-left: 3px solid #7c3aed;

  :deep(.ant-card-body) {
    padding: 14px 18px;
  }

  span {
    display: block;
    color: #718096;
    font-size: 12px;
  }

  strong {
    display: block;
    margin-top: 4px;
    color: #1d2a3f;
    font-size: 22px;
    line-height: 1.15;
  }
}

.submission-card {
  overflow: hidden;
  border-radius: 8px;
}

.candidate-name {
  color: #1d2a3f;
  font-weight: 500;
}

.candidate-username {
  margin-top: 2px;
  color: #8a98ad;
  font-size: 12px;
}

.enter-view-btn {
  margin-top: 10px;
}

.progress-value {
  color: #1d2a3f;

  &.done {
    color: #16a34a;
  }
}

.expand-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 12px;
  padding: 8px 12px;
  color: #5b6b85;
  font-size: 12px;
  background: #f6f8ff;
  border: 1px solid #e3e9ff;
  border-radius: 6px;
}

.submission-card :deep(.ant-table-row) {
  cursor: pointer;
}

.detail-wrap {
  padding: 4px 8px 8px;
  overflow-x: auto;
}

.detail-table {
  width: 100%;
  border-collapse: collapse;

  th,
  td {
    padding: 8px 10px;
    text-align: left;
    font-size: 12px;
    border-bottom: 1px solid #eef2f8;
  }

  th {
    color: #718096;
    font-weight: 500;
    background: #f8fbff;
  }

  td {
    color: #1d2a3f;
    vertical-align: top;
  }
}

.question-cell {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.question-score {
  margin-top: 2px;
  color: #8a98ad;
}

.agent-name {
  font-weight: 500;
}

.agent-link {
  margin-left: -6px;
  padding: 0;
}

.muted {
  color: #8a98ad;
  font-size: 12px;
}

.empty-state {
  margin-top: 80px;
}

.config-pre {
  max-height: 220px;
  margin: 0;
  padding: 10px 14px;
  overflow: auto;
  color: #1d2a3f;
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  background: #f6f8fa;
  border: 1px solid #e1e9f5;
  border-radius: 6px;
}

.prompt-pre {
  max-height: 420px;
}

@media (max-width: 760px) {
  .submission-admin-page {
    padding: 14px;
  }

  .page-heading {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
