<!--
  AI 阅卷（平台 admin 专用）

  内置阅卷智能体：按考试场次批量/单卷对考生智能体的真实配置（系统提示词 + 知识库 + Skill 文件）
  逐题评分，依据纯配置版量表（分项锚点 → 设计优越性系数 → AI 代做五通道判定）给出分数与理由。
  不读取截图/视频等运行证据；AI 分独立展示，管理员可按卷"采纳为复核得分"。

  @author workbuddy
-->
<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { ReloadOutlined, SearchOutlined, PlayCircleOutlined, SettingOutlined, ApiOutlined } from '@ant-design/icons-vue'
import { useAccountStore } from '@/stores'
import { isPlatformAdminUsername } from '@/utils/platformAdmin'
import { RouteNames } from '@/router'
import {
  adoptExamGrading,
  getExamGradingDetail,
  getExamGradingOverview,
  getGradingModelSettings,
  listExamCampaignsForAdmin,
  rerunExamGrading,
  saveGradingModelSettings,
  startExamGrading,
  testGradingModelSettings,
  type ExamCampaign,
  type GradingCandidateRow,
  type GradingDetail,
  type GradingOverview,
  type GradingQuestionCell,
} from '@/api/exam'

const route = useRoute()
const router = useRouter()
const accountStore = useAccountStore()

const campaigns = ref<ExamCampaign[]>([])
const selectedCampaignId = ref<string>()
const campaignsLoading = ref(false)
const overview = ref<GradingOverview | null>(null)
const overviewLoading = ref(false)
const searchKeyword = ref('')
const expandedRowKeys = ref<string[]>([])
const starting = ref(false)

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<GradingDetail | null>(null)

const settingsVisible = ref(false)
const settingsLoading = ref(false)
const settingsTesting = ref(false)
const settingsSaving = ref(false)
const settingsForm = ref({ endpoint: '', model: '', apiKey: '', enabled: true })
const settingsKeyMasked = ref('')
const settingsKeyConfigured = ref(false)
const settingsTestResult = ref<{ success: boolean; message: string } | null>(null)

async function openSettings() {
  settingsVisible.value = true
  settingsLoading.value = true
  settingsTestResult.value = null
  try {
    const res = await getGradingModelSettings()
    const data = res.data.data
    settingsForm.value = {
      endpoint: data?.endpoint || '',
      model: data?.model || '',
      apiKey: '',
      enabled: data?.enabled !== false,
    }
    settingsKeyMasked.value = data?.apiKeyMasked || ''
    settingsKeyConfigured.value = !!data?.apiKeyConfigured
  } catch (error) {
    console.error(error)
    message.error('模型设置加载失败')
  } finally {
    settingsLoading.value = false
  }
}

/** 测试连接：apiKey 留空时后端沿用已保存的 Key */
async function testSettings() {
  const form = settingsForm.value
  if (!form.endpoint.trim() || !form.model.trim()) {
    message.warning('接口地址与模型名不能为空')
    return
  }
  settingsTesting.value = true
  settingsTestResult.value = null
  try {
    const res = await testGradingModelSettings({
      endpoint: form.endpoint.trim(),
      model: form.model.trim(),
      apiKey: form.apiKey.trim() || undefined,
    })
    const result = res.data.data || {}
    settingsTestResult.value = {
      success: !!result.success,
      message: result.message || (result.success ? '连接成功' : '连接失败'),
    }
  } catch (error) {
    console.error(error)
    settingsTestResult.value = { success: false, message: '测试请求失败' }
  } finally {
    settingsTesting.value = false
  }
}

/** 保存：后端保存前会再次强制连通性测试，失败不落库 */
async function saveSettings() {
  const form = settingsForm.value
  if (!form.endpoint.trim() || !form.model.trim()) {
    message.warning('接口地址与模型名不能为空')
    return
  }
  if (!form.apiKey.trim() && !settingsKeyConfigured.value) {
    message.warning('请填写 API Key')
    return
  }
  settingsSaving.value = true
  try {
    await saveGradingModelSettings({
      endpoint: form.endpoint.trim(),
      model: form.model.trim(),
      apiKey: form.apiKey.trim() || undefined,
      enabled: form.enabled,
    })
    message.success('模型配置已保存并生效（用于后续 AI 阅卷）')
    settingsVisible.value = false
  } catch (error) {
    console.error(error)
    message.error('保存失败（连通性测试未通过或无权限），请检查配置')
  } finally {
    settingsSaving.value = false
  }
}

let refreshTimer: number | undefined

const isPlatformAdmin = computed(() =>
  isPlatformAdminUsername(accountStore.userInfo?.username),
)
const rows = computed<GradingCandidateRow[]>(() => overview.value?.rows ?? [])
const taskCounts = computed<Record<string, number>>(() => overview.value?.taskCounts ?? {})
const pendingCount = computed(
  () => (taskCounts.value['QUEUED'] ?? 0) + (taskCounts.value['RUNNING'] ?? 0),
)

const filteredRows = computed<GradingCandidateRow[]>(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  if (!keyword) return rows.value
  return rows.value.filter((row) =>
    [row.candidateName, row.loginName, row.organization, row.tenantName].some((value) =>
      value?.toLowerCase().includes(keyword),
    ),
  )
})

/** 评分中的题目数（决定是否自动刷新） */
const runningCellCount = computed(() =>
  rows.value.reduce(
    (sum, row) =>
      sum +
      (row.questions ?? []).filter(
        (cell) => cell.taskStatus === 'QUEUED' || cell.taskStatus === 'RUNNING',
      ).length,
    0,
  ),
)

/** 进行中/待评任务存在时每 5 秒自动刷新进度 */
function ensureRefreshTimer() {
  if (refreshTimer !== undefined) return
  refreshTimer = window.setInterval(() => {
    if (selectedCampaignId.value && runningCellCount.value > 0) {
      void loadOverview(true)
    }
  }, 5000)
}

function stopRefreshTimer() {
  if (refreshTimer !== undefined) {
    window.clearInterval(refreshTimer)
    refreshTimer = undefined
  }
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
    } else {
      selectedCampaignId.value = campaigns.value[0] ? String(campaigns.value[0].id) : undefined
    }
    await loadOverview()
  } catch {
    message.warning('加载考试批次失败')
  } finally {
    campaignsLoading.value = false
  }
}

async function loadOverview(silent = false) {
  if (!selectedCampaignId.value) return
  if (!silent) overviewLoading.value = true
  try {
    const res = await getExamGradingOverview(selectedCampaignId.value)
    overview.value = res.data.data ?? null
    if (!silent && rows.value.length) {
      const first = rows.value[0]
      if (first?.enrollmentId != null) {
        expandedRowKeys.value = [String(first.enrollmentId)]
      }
    }
  } catch (error) {
    console.error(error)
    if (!silent) message.error('AI 阅卷总览加载失败')
  } finally {
    if (!silent) overviewLoading.value = false
  }
}

function onCampaignChange() {
  overview.value = null
  expandedRowKeys.value = []
  void loadOverview()
}

function startGrading() {
  if (!selectedCampaignId.value) return
  const gradable = rows.value.reduce(
    (sum, row) => sum + (row.questions ?? []).filter((cell) => !!cell.submissionId).length,
    0,
  )
  if (gradable === 0) {
    message.warning('本场次暂无已提交且关联智能体的答卷')
    return
  }
  Modal.confirm({
    title: '开始 AI 阅卷？',
    content: `将对本场次约 ${gradable} 份已提交答卷逐卷调用大模型评分（每卷一次调用，约需数分钟完成）。已有进行中任务的答卷会自动跳过，本页每 5 秒自动刷新进度。`,
    okText: '开始评分',
    cancelText: '取消',
    onOk: async () => {
      starting.value = true
      try {
        const res = await startExamGrading(selectedCampaignId.value!)
        const counts = res.data.data ?? {}
        message.success(
          `已入队 ${counts.enqueued ?? 0} 份答卷${
            counts.skippedRunning ? `（跳过进行中 ${counts.skippedRunning} 份）` : ''
          }`,
        )
        await loadOverview(true)
        ensureRefreshTimer()
      } catch (error) {
        console.error(error)
        message.error('AI 阅卷任务入队失败')
      } finally {
        starting.value = false
      }
    },
  })
}

async function rerunCell(cell: GradingQuestionCell) {
  if (!cell.submissionId) return
  try {
    await rerunExamGrading(cell.submissionId)
    message.success('已重新入队评分')
    await loadOverview(true)
    ensureRefreshTimer()
  } catch (error) {
    console.error(error)
    message.error('重新评分失败')
  }
}

function adoptCell(cell: GradingQuestionCell) {
  if (!cell.submissionId) return
  Modal.confirm({
    title: '采纳为复核得分？',
    content: `将把 AI 阅卷得分 ${formatScore(cell.totalScore)} / ${formatScore(
      cell.configMaxScore,
    )} 写入本题复核得分（题目满分 ${cell.questionScore ?? 0} 分），可在"成绩与发布"页查看。`,
    okText: '采纳',
    cancelText: '取消',
    onOk: async () => {
      try {
        await adoptExamGrading(cell.submissionId!)
        message.success('已采纳为复核得分')
      } catch (error) {
        console.error(error)
        message.error('采纳失败')
      }
    },
  })
}

async function openDetail(cell: GradingQuestionCell) {
  if (!cell.submissionId) return
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const res = await getExamGradingDetail(cell.submissionId)
    detail.value = res.data.data ?? null
  } catch (error) {
    console.error(error)
    message.error('评分详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

function abnormalCount(row: GradingCandidateRow): number {
  return (row.questions ?? []).filter((cell) => (cell.integrityFlags ?? []).length > 0).length
}

function taskStatusColor(status?: string): string {
  switch (status) {
    case 'SUCCEEDED':
      return 'success'
    case 'RUNNING':
      return 'processing'
    case 'QUEUED':
      return 'default'
    case 'FAILED':
      return 'error'
    default:
      return 'warning'
  }
}

function taskStatusLabel(cell: GradingQuestionCell): string {
  if (cell.taskStatus === 'SUCCEEDED') return '已评分'
  if (cell.taskStatus === 'RUNNING') return '评分中'
  if (cell.taskStatus === 'QUEUED') return '排队中'
  if (cell.taskStatus === 'FAILED') return '评分失败'
  return cell.taskStatus ?? '—'
}

const dominanceColors: Record<string, string> = {
  确认外包设计: 'red',
  疑似外包设计: 'orange',
  中度嫌疑: 'gold',
  轻度残留: 'cyan',
  工具型使用: 'green',
}

/** 主导权结论的通俗说法（面向非技术读者） */
const dominanceLabels: Record<string, string> = {
  确认外包设计: '确认 AI 代做',
  疑似外包设计: '疑似 AI 代做',
  中度嫌疑: '中度怀疑 AI 代做',
  轻度残留: '有轻微 AI 痕迹',
  工具型使用: '本人独立设计',
}

function dominanceLabel(status?: string): string {
  if (!status) return ''
  return dominanceLabels[status] ?? status
}

function formatScore(value?: number | null): string {
  if (value == null) return '—'
  return Number.isInteger(value) ? String(value) : value.toFixed(1)
}

onMounted(() => {
  void loadCampaigns()
  ensureRefreshTimer()
})

onUnmounted(() => {
  stopRefreshTimer()
})
</script>

<template>
  <div class="exam-grading-page">
    <a-alert type="info" show-icon class="page-hint">
      <template #message>
        AI 阅卷基于考生智能体的<b>真实配置</b>（系统提示词 / 知识库 / Skill 文件）按纯配置量表评分并给出理由，
        不读取截图、视频等运行证据；AI 分独立展示，确认后可按卷"采纳为复核得分"。
      </template>
    </a-alert>

    <div class="toolbar">
      <a-select
        v-model:value="selectedCampaignId"
        :loading="campaignsLoading"
        :options="campaigns.map((item) => ({ value: String(item.id), label: item.name }))"
        placeholder="选择考试场次"
        class="campaign-select"
        @change="onCampaignChange"
      />
      <a-input
        v-model:value="searchKeyword"
        placeholder="搜索考生姓名 / 考号 / 组织"
        class="search-input"
        allow-clear
      >
        <template #prefix><SearchOutlined /></template>
      </a-input>
      <a-button :loading="overviewLoading" @click="loadOverview()">
        <template #icon><ReloadOutlined /></template>
        刷新
      </a-button>
      <a-button
        type="primary"
        :loading="starting"
        :disabled="!selectedCampaignId"
        @click="startGrading"
      >
        <template #icon><PlayCircleOutlined /></template>
        开始 AI 阅卷
      </a-button>
      <a-button @click="openSettings">
        <template #icon><SettingOutlined /></template>
        模型设置
      </a-button>
    </div>

    <div class="stats-row">
      <a-tag color="default">排队中 {{ taskCounts['QUEUED'] ?? 0 }}</a-tag>
      <a-tag color="processing">评分中 {{ taskCounts['RUNNING'] ?? 0 }}</a-tag>
      <a-tag color="success">已完成 {{ taskCounts['SUCCEEDED'] ?? 0 }}</a-tag>
      <a-tag color="error">失败 {{ taskCounts['FAILED'] ?? 0 }}</a-tag>
      <span v-if="runningCellCount > 0" class="auto-refresh-hint">评分进行中，每 5 秒自动刷新…</span>
    </div>

    <a-table
      :data-source="filteredRows"
      :loading="overviewLoading"
      :row-key="(row: GradingCandidateRow) => String(row.enrollmentId)"
      :expanded-row-keys="expandedRowKeys"
      :pagination="{ pageSize: 20, showSizeChanger: false }"
      expand-row-by-click
      class="candidate-table"
      @expanded-rows-change="(keys: any[]) => (expandedRowKeys = keys.map(String))"
    >
      <a-table-column title="考生" data-index="candidateName" :width="140">
        <template #default="{ record }">
          <span class="candidate-name">{{ record.candidateName || '未命名考生' }}</span>
        </template>
      </a-table-column>
      <a-table-column title="考号" data-index="loginName" :width="130" />
      <a-table-column title="组织" data-index="organization" :width="140">
        <template #default="{ record }">{{ record.organization || '—' }}</template>
      </a-table-column>
      <a-table-column title="已评 / 已交" :width="110">
        <template #default="{ record }">
          {{ (record.questions ?? []).filter((c: GradingQuestionCell) => c.graded).length }} /
          {{ record.submittedCount ?? 0 }}
        </template>
      </a-table-column>
      <a-table-column title="总分" :width="130">
        <template #default="{ record }">
          <b v-if="record.totalScore != null"
            >{{ formatScore(record.totalScore) }} / {{ formatScore(record.totalMaxScore) }}</b
          >
          <span v-else>未评分</span>
        </template>
      </a-table-column>
      <a-table-column title="异常" :width="90">
        <template #default="{ record }">
          <a-tag v-if="abnormalCount(record) > 0" color="red">{{ abnormalCount(record) }}</a-tag>
          <span v-else>0</span>
        </template>
      </a-table-column>

      <template #expandedRowRender="{ record }">
        <div class="question-cells" @click.stop>
          <a-table
            :data-source="record.questions ?? []"
            :row-key="(cell: GradingQuestionCell) => `${record.enrollmentId}-${cell.questionNo}`"
            :pagination="false"
            size="small"
          >
            <a-table-column title="题目" :width="180">
              <template #default="{ record: cell }">
                第{{ cell.questionNo }}题 {{ cell.questionTitle || '' }}
              </template>
            </a-table-column>
            <a-table-column title="智能体" :width="160">
              <template #default="{ record: cell }">{{ cell.agentName || '—' }}</template>
            </a-table-column>
            <a-table-column title="状态" :width="110">
              <template #default="{ record: cell }">
                <a-tooltip :title="cell.taskErrorMessage || ''">
                  <a-badge :status="taskStatusColor(cell.taskStatus)" :text="taskStatusLabel(cell)" />
                </a-tooltip>
              </template>
            </a-table-column>
            <a-table-column title="得分" :width="110">
              <template #default="{ record: cell }">
                <b v-if="cell.graded">{{ formatScore(cell.totalScore) }} / {{ formatScore(cell.configMaxScore) }}</b>
                <span v-else>—</span>
              </template>
            </a-table-column>
            <a-table-column title="AI 代做风险" :width="150">
              <template #default="{ record: cell }">
                <a-tag
                  v-if="cell.dominanceStatus"
                  :color="dominanceColors[cell.dominanceStatus] ?? 'default'"
                >
                  {{ dominanceLabel(cell.dominanceStatus) }}
                </a-tag>
                <span v-else>—</span>
              </template>
            </a-table-column>
            <a-table-column title="异常标记" :width="130">
              <template #default="{ record: cell }">
                <template v-if="(cell.integrityFlags ?? []).length">
                  <a-tooltip :title="(cell.integrityFlags ?? []).join('；')">
                    <a-tag color="red">{{ (cell.integrityFlags ?? []).length }} 项</a-tag>
                  </a-tooltip>
                </template>
                <span v-else>—</span>
              </template>
            </a-table-column>
            <a-table-column title="操作" :width="220">
              <template #default="{ record: cell }">
                <a-space>
                  <a-button size="small" type="link" :disabled="!cell.graded" @click="openDetail(cell)">
                    详情
                  </a-button>
                  <a-button
                    size="small"
                    type="link"
                    :disabled="!cell.submissionId || cell.taskStatus === 'QUEUED' || cell.taskStatus === 'RUNNING'"
                    @click="rerunCell(cell)"
                  >
                    评分 / 重评
                  </a-button>
                  <a-button
                    size="small"
                    type="primary"
                    ghost
                    :disabled="!cell.graded"
                    @click="adoptCell(cell)"
                  >
                    采纳
                  </a-button>
                </a-space>
              </template>
            </a-table-column>
          </a-table>
        </div>
      </template>
    </a-table>

    <a-drawer v-model:open="detailVisible" title="AI 阅卷详情" width="720" destroy-on-close>
      <a-spin :spinning="detailLoading">
        <template v-if="detail">
          <div class="detail-header">
            <div class="detail-title">
              {{ detail.candidateName || '考生' }}
              <span class="detail-sub">
                考号 {{ detail.loginName || '—' }} · 第{{ detail.questionNo }}题 {{ detail.questionTitle || '' }}
              </span>
            </div>
            <a-space wrap>
              <a-tag
                v-if="detail.dominanceStatus"
                :color="dominanceColors[detail.dominanceStatus] ?? 'default'"
              >
                {{ dominanceLabel(detail.dominanceStatus) }}
              </a-tag>
              <a-tag v-for="flag in detail.integrityFlags ?? []" :key="flag" color="red">{{ flag }}</a-tag>
            </a-space>
          </div>

          <div class="comment-card">
            <div class="comment-label">AI 评语</div>
            <p class="comment-text">
              {{ detail.plainComment || (detail.summary ? '（本卷为旧版评分结果，暂无通俗评语，可点击「重新评分」生成）' : '暂无评语') }}
            </p>
          </div>

          <div class="score-line">
            本题得分 <b class="score-final">{{ formatScore(detail.totalScore) }} / {{ formatScore(detail.configMaxScore) }}</b>
          </div>

          <div class="detail-actions">
            <a-space>
              <a-button
                type="primary"
                :disabled="!detail.totalScore"
                @click="adoptCell({ submissionId: detail.submissionId, totalScore: detail.totalScore, configMaxScore: detail.configMaxScore, questionScore: detail.questionScore, graded: true })"
              >
                采纳为复核得分
              </a-button>
              <a-button
                @click="rerunCell({ submissionId: detail.submissionId })"
              >
                重新评分
              </a-button>
            </a-space>
          </div>

          <a-collapse ghost class="tech-collapse">
            <a-collapse-panel key="tech" header="技术评分明细（供管理员核查）">
              <div class="score-chain">
                分项基础分 <b>{{ formatScore(detail.baseScore) }}</b>
                × 优越性系数 <b>{{ detail.coefficient ?? '—' }}</b>
                <template v-if="detail.capRatio">（封顶 {{ detail.capRatio }}：{{ detail.capStatus }}）</template>
                − 减分 <b>{{ formatScore(detail.deductionAmount ?? 0) }}</b>
                = <b class="score-final">{{ formatScore(detail.totalScore) }} / {{ formatScore(detail.configMaxScore) }}</b>
              </div>

              <a-descriptions :column="2" size="small" class="detail-meta">
                <a-descriptions-item label="智能体">{{ detail.agentName || '—' }}</a-descriptions-item>
                <a-descriptions-item label="模型">{{ detail.modelName || '—' }}</a-descriptions-item>
                <a-descriptions-item label="置信度">{{ detail.confidence || '—' }}</a-descriptions-item>
                <a-descriptions-item label="量表版本">{{ detail.rubricVersion || '—' }}</a-descriptions-item>
              </a-descriptions>

              <h4 class="section-title">分项评分与依据</h4>
              <a-table
                :data-source="detail.items ?? []"
                :row-key="(item: any) => item.name"
                :pagination="false"
                size="small"
              >
                <a-table-column title="分项" data-index="name" :width="150" />
                <a-table-column title="得分" :width="80">
                  <template #default="{ record: item }">
                    {{ formatScore(item.score) }} / {{ formatScore(item.max) }}
                  </template>
                </a-table-column>
                <a-table-column title="证据" data-index="evidence" />
              </a-table>

              <h4 class="section-title">设计优越性清单（命中 {{ detail.checklistHits ?? 0 }}/4）</h4>
              <ul class="checklist">
                <li v-for="(item, index) in detail.checklistDetail ?? []" :key="index">{{ item }}</li>
              </ul>

              <template v-if="(detail.dominanceChannels ?? []).length">
                <h4 class="section-title">AI 代做判定依据</h4>
                <ul class="checklist">
                  <li v-for="(channel, index) in detail.dominanceChannels ?? []" :key="index">
                    <b>通道 {{ channel.channel }}</b>：{{ channel.finding }}
                    <span v-if="channel.evidence" class="channel-evidence">（{{ channel.evidence }}）</span>
                  </li>
                </ul>
              </template>

              <template v-if="detail.deductionReason">
                <h4 class="section-title">减分原因</h4>
                <p class="summary-text">{{ detail.deductionReason }}</p>
              </template>

              <h4 class="section-title">技术摘要</h4>
              <p class="summary-text">{{ detail.summary || '—' }}</p>
            </a-collapse-panel>
          </a-collapse>
        </template>
        <a-empty v-else-if="!detailLoading" description="暂无评分结果" />
      </a-spin>
    </a-drawer>
    <a-modal
      v-model:open="settingsVisible"
      title="AI 阅卷模型设置"
      width="560"
      :confirm-loading="settingsSaving"
      ok-text="测试通过并保存"
      cancel-text="取消"
      @ok="saveSettings"
    >
      <a-spin :spinning="settingsLoading">
        <a-alert
          type="info"
          show-icon
          class="settings-hint"
          message="配置保存前会自动进行连通性测试，测试通过才会生效；修改后无需重启，立即用于后续 AI 阅卷。"
        />
        <a-form layout="vertical" class="settings-form">
          <a-form-item label="接口地址（OpenAI 兼容 /chat/completions）" required>
            <a-input
              v-model:value="settingsForm.endpoint"
              placeholder="https://api.deepseek.com/chat/completions"
            />
          </a-form-item>
          <a-form-item label="API Key" :extra="settingsKeyConfigured ? `已配置：${settingsKeyMasked}，留空表示保留现有 Key` : ''">
            <a-input-password
              v-model:value="settingsForm.apiKey"
              :placeholder="settingsKeyConfigured ? '留空表示保留现有 Key' : 'sk-...'"
            />
          </a-form-item>
          <a-form-item label="模型名" required>
            <a-input v-model:value="settingsForm.model" placeholder="例如：deepseek-flash" />
          </a-form-item>
          <a-form-item label="启用 AI 阅卷">
            <a-switch v-model:checked="settingsForm.enabled" />
          </a-form-item>
          <a-form-item>
            <a-button :loading="settingsTesting" @click="testSettings">
              <template #icon><ApiOutlined /></template>
              测试连接
            </a-button>
            <span v-if="settingsTestResult" :class="['test-result', settingsTestResult.success ? 'test-ok' : 'test-fail']">
              {{ settingsTestResult.success ? '✓' : '✗' }} {{ settingsTestResult.message }}
            </span>
          </a-form-item>
        </a-form>
      </a-spin>
    </a-modal>
  </div>
</template>

<style scoped lang="scss">
.exam-grading-page {
  padding: 16px 20px;
}

.page-hint {
  margin-bottom: 12px;
}

.toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;

  .campaign-select {
    width: 320px;
  }

  .search-input {
    width: 260px;
  }
}

.stats-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;

  .auto-refresh-hint {
    color: #0f6e56;
    font-size: 12px;
  }
}

.candidate-name {
  font-weight: 500;
}

.question-cells {
  padding: 4px 8px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;

  .detail-title {
    font-size: 16px;
    font-weight: 500;

    .detail-sub {
      display: block;
      margin-top: 4px;
      font-size: 12px;
      font-weight: 400;
      color: #888780;
    }
  }
}

.comment-card {
  padding: 14px 16px;
  margin-bottom: 12px;
  background: #f0f7f4;
  border: 0.5px solid #9fe1cb;
  border-radius: 10px;

  .comment-label {
    margin-bottom: 6px;
    font-size: 13px;
    font-weight: 500;
    color: #0f6e56;
  }

  .comment-text {
    margin: 0;
    font-size: 13px;
    line-height: 1.8;
    white-space: pre-wrap;
  }
}

.score-line {
  margin-bottom: 12px;
  font-size: 14px;

  .score-final {
    color: #0f6e56;
  }
}

.tech-collapse {
  margin-top: 8px;
}

.score-chain {
  padding: 10px 14px;
  margin-bottom: 12px;
  background: #f5f5f2;
  border-radius: 8px;
  font-size: 13px;

  .score-final {
    color: #0f6e56;
  }
}

.detail-meta {
  margin-bottom: 12px;
}

.section-title {
  margin: 16px 0 8px;
  font-size: 13px;
  font-weight: 500;
}

.checklist {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.8;

  .channel-evidence {
    color: #888780;
    font-size: 12px;
  }
}

.summary-text {
  margin: 0;
  padding: 10px 14px;
  background: #f5f5f2;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.detail-actions {
  margin-top: 20px;
}

.settings-hint {
  margin-bottom: 14px;
}

.test-result {
  margin-left: 12px;
  font-size: 12px;
  word-break: break-all;

  &.test-ok {
    color: #0f6e56;
  }

  &.test-fail {
    color: #a32d2d;
  }
}
</style>
