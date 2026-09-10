<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  exportReviewerScores,
  getEnrollmentScoreDetail,
  listExamCampaignsForAdmin,
  listReviewerScores,
  saveReviewerScore,
  type ExamCampaign,
  type ExamQuestionScore,
  type ExamScoreRow,
  type ExamSubmissionDetail,
} from '@/api/exam'
import { normalizeCommunicationUrl } from '@/utils/communication'

const loading = ref(false)
const rows = ref<ExamScoreRow[]>([])
const nameFilter = ref('')
const campaigns = ref<ExamCampaign[]>([])
const campaignLoading = ref(false)
const selectedCampaignId = ref<string>()
const exporting = ref(false)

async function loadCampaigns() {
  campaignLoading.value = true
  try {
    const res = await listExamCampaignsForAdmin()
    campaigns.value = res.data?.data || []
    const firstCampaign = campaigns.value[0]
    if (!selectedCampaignId.value && firstCampaign?.id != null) {
      selectedCampaignId.value = String(firstCampaign.id)
    }
    await load()
  } catch {
    message.error('加载考试场次失败')
  } finally {
    campaignLoading.value = false
  }
}

async function load() {
  if (!selectedCampaignId.value) {
    rows.value = []
    return
  }
  loading.value = true
  try {
    const res = await listReviewerScores(
      selectedCampaignId.value,
      nameFilter.value.trim() || undefined,
    )
    rows.value = res.data?.data || []
  } catch {
    message.error('加载学员成绩失败')
  } finally {
    loading.value = false
  }
}

function onSearch() {
  void load()
}

function onCampaignChange() {
  void load()
}

function selectedCampaignName() {
  return (
    campaigns.value.find((campaign) => String(campaign.id) === selectedCampaignId.value)?.name ||
    '考试'
  )
}

function safeFilenamePart(value: string) {
  return value.replace(/[\\/:*?"<>|]/g, '_').slice(0, 80)
}

async function exportScores() {
  if (!selectedCampaignId.value) {
    message.warning('请先选择考试场次')
    return
  }
  exporting.value = true
  try {
    const res = await exportReviewerScores(selectedCampaignId.value)
    const blob = res.data as unknown as Blob
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${safeFilenamePart(selectedCampaignName())}-成绩汇总.xlsx`
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.setTimeout(() => window.URL.revokeObjectURL(url), 0)
    message.success('成绩 Excel 已导出')
  } catch (error: unknown) {
    message.error(errorMessage(error, '导出成绩失败'))
  } finally {
    exporting.value = false
  }
}

function scoreText(score?: number, limit?: number) {
  return score == null ? '—' : `${score} / ${limit ?? 0}`
}

function progressText(row: ExamScoreRow) {
  const submitted = row.submittedQuestionCount ?? 0
  const total = row.questionCount ?? submitted
  return `${submitted} / ${total} 题`
}

function reviewStatus(row: ExamScoreRow) {
  const reviewed = row.reviewedQuestionCount ?? 0
  const submitted = row.submittedQuestionCount ?? 0
  if (submitted > 0 && reviewed === submitted) return '已复核'
  if (reviewed > 0) return `已复核 ${reviewed}/${submitted}`
  return '未复核'
}

function reviewStatusType(row: ExamScoreRow): 'success' | 'warning' | 'default' {
  const reviewed = row.reviewedQuestionCount ?? 0
  const submitted = row.submittedQuestionCount ?? 0
  if (submitted > 0 && reviewed === submitted) return 'success'
  if (reviewed > 0) return 'warning'
  return 'default'
}

// 复核弹窗：最终得分直接按本题配置的考试分值录入和保存。
const editVisible = ref(false)
const editing = ref<ExamQuestionScore | null>(null)
const finalScore = ref<number | null>(0)
const saving = ref(false)

function openEdit(question: ExamQuestionScore) {
  if (question.submissionId == null) {
    message.warning('该题没有可复核的提交记录')
    return
  }
  editing.value = question
  finalScore.value = question.finalScore ?? question.aiScore ?? 0
  editVisible.value = true
}

function errorMessage(error: unknown, fallback = '保存失败') {
  const response = error as { response?: { data?: { msg?: string } } }
  return response.response?.data?.msg || fallback
}

async function refreshDetail() {
  const enrollmentId = detail.value?.enrollmentId
  if (enrollmentId == null) return
  const res = await getEnrollmentScoreDetail(enrollmentId)
  detail.value = res.data?.data || null
}

async function submitEdit() {
  const submissionId = editing.value?.submissionId
  const score = finalScore.value
  if (submissionId == null || score == null) return
  const limit = editing.value?.questionScore ?? 100
  if (score < 0 || score > limit) {
    message.warning(`最终得分需在 0 ~ ${limit} 之间`)
    return
  }
  saving.value = true
  try {
    await saveReviewerScore(submissionId, score)
    message.success('已保存最终得分')
    editVisible.value = false
    await load()
    await refreshDetail()
    if (selectedQuestion.value?.submissionId === submissionId) {
      selectedQuestion.value =
        detail.value?.questions?.find((question) => question.submissionId === submissionId) || null
    }
  } catch (error: unknown) {
    message.error(errorMessage(error))
  } finally {
    saving.value = false
  }
}

// 学员总览详情
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<ExamSubmissionDetail | null>(null)

async function openDetail(row: ExamScoreRow) {
  if (row.enrollmentId == null) {
    message.warning('该记录缺少考试报名信息，暂时无法查看学员总览')
    return
  }
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res = await getEnrollmentScoreDetail(row.enrollmentId)
    detail.value = res.data?.data || null
  } catch {
    message.error('加载详情失败')
  } finally {
    detailLoading.value = false
  }
}

// 单题/智能体详情
const questionDetailVisible = ref(false)
const selectedQuestion = ref<ExamQuestionScore | null>(null)

function openQuestionDetail(question: ExamQuestionScore) {
  selectedQuestion.value = question
  questionDetailVisible.value = true
}

function hasScores(scores?: Record<string, number>) {
  return !!scores && Object.keys(scores).length > 0
}

function scorePercent(score: number, limit?: number) {
  const maximum = limit && limit > 0 ? limit : 100
  return `${Math.min(100, Math.max(0, (score / maximum) * 100))}%`
}

function fileListLabel(files?: { url?: string; name?: string }[]) {
  if (!files || !files.length) return '—'
  return files.map((file) => file.name || file.url?.split('/').pop() || '文件').join('、')
}

const columns = [
  { title: '姓名', dataIndex: 'candidateName', key: 'candidateName' },
  { title: '身份证号', dataIndex: 'identityNumber', key: 'identityNumber' },
  { title: '联系电话', dataIndex: 'phone', key: 'phone' },
  { title: '所属单位 / 团队', dataIndex: 'organization', key: 'organization' },
  { title: '提交进度', key: 'submissionProgress' },
  { title: '演示地址', key: 'demoUrl' },
  { title: 'AI 自评总分', key: 'aiTotalScore' },
  { title: '最终审核总分', key: 'finalScore' },
  { title: '复核状态', key: 'reviewed' },
  { title: '最近提交时间', dataIndex: 'submittedAt', key: 'submittedAt' },
  { title: '操作', key: 'action', width: 90 },
]

const questionColumns = [
  { title: '题号', key: 'questionNo', width: 64 },
  { title: '题目', key: 'questionTitle', width: 190 },
  { title: '智能体 / 作品', key: 'work', width: 170 },
  { title: '演示地址', key: 'demoUrl', width: 100 },
  { title: 'AI 自评', key: 'aiScore', width: 110 },
  { title: '最终审核', key: 'finalScore', width: 110 },
  { title: '操作', key: 'action', width: 116 },
]

onMounted(loadCampaigns)
</script>

<template>
  <div class="reviewer-score-page">
    <div class="rsp-header">
      <div>
        <h2 class="rsp-title">学员成绩复核</h2>
        <p class="rsp-sub">
          按学员汇总考试总分；点击演示地址、AI 自评或最终审核，可查看每道题/每个智能体的具体成绩。
        </p>
      </div>
      <div class="rsp-search">
        <a-select
          v-model:value="selectedCampaignId"
          :loading="campaignLoading"
          placeholder="选择考试场次"
          style="width: 240px"
          @change="onCampaignChange"
        >
          <a-select-option
            v-for="campaign in campaigns"
            :key="campaign.id"
            :value="String(campaign.id)"
          >
            {{ campaign.name || '未命名考试' }}
          </a-select-option>
        </a-select>
        <a-input-search
          v-model:value="nameFilter"
          placeholder="按考生姓名筛选"
          allow-clear
          enter-button="查询"
          style="width: 280px"
          @search="onSearch"
        />
        <a-button :disabled="!selectedCampaignId" @click="load">刷新</a-button>
        <a-button
          type="primary"
          :loading="exporting"
          :disabled="!selectedCampaignId"
          @click="exportScores"
        >
          导出成绩 Excel
        </a-button>
      </div>
    </div>

    <a-table
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      row-key="enrollmentId"
      :pagination="{ pageSize: 10, showTotal: (total: number) => `共 ${total} 名学员` }"
      bordered
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'submissionProgress'">
          {{ progressText(record) }}
        </template>
        <template v-else-if="column.key === 'demoUrl'">
          <a-button type="link" size="small" class="score-link" @click="openDetail(record)">
            {{ record.demoCount ? `查看 ${record.demoCount} 个演示` : '查看作品明细' }}
          </a-button>
        </template>
        <template v-else-if="column.key === 'aiTotalScore'">
          <a-button
            v-if="record.aiTotalScore != null"
            type="link"
            size="small"
            class="score-link"
            @click="openDetail(record)"
          >
            {{ scoreText(record.aiTotalScore, record.totalLimit) }}
            <span class="score-progress"
              >（{{ record.aiScoredQuestionCount ?? 0 }}/{{
                record.submittedQuestionCount ?? 0
              }}题）</span
            >
          </a-button>
          <a-button v-else type="link" size="small" class="score-link" @click="openDetail(record)">
            未评测
          </a-button>
        </template>
        <template v-else-if="column.key === 'finalScore'">
          <a-button
            v-if="record.finalScore != null"
            type="link"
            size="small"
            class="score-link"
            @click="openDetail(record)"
          >
            {{ scoreText(record.finalScore, record.totalLimit) }}
            <span class="score-progress"
              >（{{ record.reviewedQuestionCount ?? 0 }}/{{
                record.submittedQuestionCount ?? 0
              }}题）</span
            >
          </a-button>
          <a-button v-else type="link" size="small" class="score-link" @click="openDetail(record)">
            未复核
          </a-button>
        </template>
        <template v-else-if="column.key === 'reviewed'">
          <a-badge :status="reviewStatusType(record)" :text="reviewStatus(record)" />
        </template>
        <template v-else-if="column.key === 'action'">
          <a-button type="link" size="small" @click="openDetail(record)">详情</a-button>
        </template>
      </template>
    </a-table>

    <!-- 按题目展开的学员总览 -->
    <a-drawer
      v-model:open="detailVisible"
      title="学员成绩总览"
      :width="980"
      placement="right"
      :body-style="{ padding: '0 24px 24px' }"
    >
      <a-spin :spinning="detailLoading">
        <template v-if="detail">
          <a-divider orientation="left">考生信息</a-divider>
          <a-descriptions :column="2" size="small" bordered :label-style="{ width: '108px' }">
            <a-descriptions-item label="姓名">{{
              detail.candidateName || '—'
            }}</a-descriptions-item>
            <a-descriptions-item label="身份证号">{{
              detail.identityNumber || '—'
            }}</a-descriptions-item>
            <a-descriptions-item label="联系电话">{{ detail.phone || '—' }}</a-descriptions-item>
            <a-descriptions-item label="所属单位 / 团队">{{
              detail.organization || '—'
            }}</a-descriptions-item>
            <a-descriptions-item label="登录名">{{ detail.loginName || '—' }}</a-descriptions-item>
            <a-descriptions-item label="所属租户">{{
              detail.tenantName || '—'
            }}</a-descriptions-item>
          </a-descriptions>

          <div class="summary-grid">
            <div class="summary-item">
              <span class="summary-label">提交进度</span>
              <strong
                >{{ detail.submittedQuestionCount ?? 0 }} /
                {{ detail.questionCount ?? 0 }} 题</strong
              >
            </div>
            <div class="summary-item">
              <span class="summary-label">考试总分上限</span>
              <strong>{{ detail.totalLimit ?? 0 }}</strong>
            </div>
            <div class="summary-item">
              <span class="summary-label">AI 自评总分</span>
              <strong v-if="detail.aiTotalScore != null"
                >{{ detail.aiTotalScore }} / {{ detail.totalLimit ?? 0 }}</strong
              >
              <strong v-else class="muted">未评测</strong>
              <small>{{ detail.aiScoredQuestionCount ?? 0 }} 题已评</small>
            </div>
            <div class="summary-item">
              <span class="summary-label">最终审核总分</span>
              <strong v-if="detail.finalScore != null"
                >{{ detail.finalScore }} / {{ detail.totalLimit ?? 0 }}</strong
              >
              <strong v-else class="muted">未复核</strong>
              <small>{{ detail.reviewedQuestionCount ?? 0 }} 题已复核</small>
            </div>
          </div>

          <a-alert
            type="info"
            show-icon
            message="总分说明"
            description="AI 初评按 100 分量表折算为各题分值；最终复核分直接按各题配置的分值录入和汇总。进入题目详情可查看 AI 原始分。"
            style="margin-top: 16px"
          />

          <a-divider orientation="left">题目 / 智能体成绩明细</a-divider>
          <a-table
            v-if="detail.questions?.length"
            :columns="questionColumns"
            :data-source="detail.questions"
            row-key="submissionId"
            size="small"
            bordered
            :pagination="false"
            :scroll="{ x: 820 }"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'questionNo'">
                第 {{ record.questionNo }} 题
              </template>
              <template v-else-if="column.key === 'questionTitle'">
                <div class="question-title">{{ record.questionTitle || '未关联试题' }}</div>
                <div v-if="record.questionScenario" class="question-meta">
                  {{ record.questionScenario }}
                </div>
              </template>
              <template v-else-if="column.key === 'work'">
                <div>{{ record.agentName || '未关联智能体' }}</div>
                <div class="question-meta">{{ record.workName || '未填写作品名称' }}</div>
              </template>
              <template v-else-if="column.key === 'demoUrl'">
                <a v-if="record.demoUrl" :href="normalizeCommunicationUrl(record.demoUrl)" target="_blank" rel="noopener"
                  >打开演示</a
                >
                <a-button v-else type="link" size="small" @click="openQuestionDetail(record)"
                  >查看详情</a-button
                >
              </template>
              <template v-else-if="column.key === 'aiScore'">
                <a-button
                  type="link"
                  size="small"
                  class="score-link"
                  @click="openQuestionDetail(record)"
                >
                  {{
                    record.aiScore != null
                      ? scoreText(record.aiScore, record.questionScore)
                      : '未评测'
                  }}
                </a-button>
              </template>
              <template v-else-if="column.key === 'finalScore'">
                <a-button
                  type="link"
                  size="small"
                  class="score-link"
                  @click="openQuestionDetail(record)"
                >
                  {{
                    record.finalScore != null
                      ? scoreText(record.finalScore, record.questionScore)
                      : '未复核'
                  }}
                </a-button>
              </template>
              <template v-else-if="column.key === 'action'">
                <a-space :size="0">
                  <a-button type="link" size="small" @click="openQuestionDetail(record)"
                    >详情</a-button
                  >
                  <a-button type="link" size="small" @click="openEdit(record)">复核</a-button>
                </a-space>
              </template>
            </template>
          </a-table>
          <a-empty v-else description="暂无已提交题目" />
        </template>
      </a-spin>
    </a-drawer>

    <!-- 单题/智能体详情与评分细项 -->
    <a-modal
      v-model:open="questionDetailVisible"
      :title="selectedQuestion ? `第 ${selectedQuestion.questionNo} 题 · 评分详情` : '评分详情'"
      :footer="null"
      width="820"
    >
      <template v-if="selectedQuestion">
        <a-descriptions :column="2" size="small" bordered :label-style="{ width: '108px' }">
          <a-descriptions-item label="题目" :span="2">{{
            selectedQuestion.questionTitle || '未关联试题'
          }}</a-descriptions-item>
          <a-descriptions-item label="题目分值"
            >{{ selectedQuestion.questionScore ?? 0 }} 分</a-descriptions-item
          >
          <a-descriptions-item label="提交状态">{{
            selectedQuestion.submissionStatus || '—'
          }}</a-descriptions-item>
          <a-descriptions-item label="关联智能体">{{
            selectedQuestion.agentName || '—'
          }}</a-descriptions-item>
          <a-descriptions-item label="作品名称">{{
            selectedQuestion.workName || '—'
          }}</a-descriptions-item>
          <a-descriptions-item label="提交时间" :span="2">{{
            selectedQuestion.submittedAt || '—'
          }}</a-descriptions-item>
          <a-descriptions-item label="演示地址" :span="2">
            <a
              v-if="selectedQuestion.demoUrl"
              :href="normalizeCommunicationUrl(selectedQuestion.demoUrl)"
              target="_blank"
              rel="noopener"
              style="word-break: break-all"
            >
              {{ normalizeCommunicationUrl(selectedQuestion.demoUrl) }}
            </a>
            <span v-else class="muted">—</span>
          </a-descriptions-item>
        </a-descriptions>

        <a-divider orientation="left">作品提交信息</a-divider>
        <a-descriptions :column="1" size="small" bordered :label-style="{ width: '108px' }">
          <a-descriptions-item label="应用场景">{{
            selectedQuestion.scene || '—'
          }}</a-descriptions-item>
          <a-descriptions-item label="功能说明">{{
            selectedQuestion.functionDescription || '—'
          }}</a-descriptions-item>
          <a-descriptions-item label="创新性说明">{{
            selectedQuestion.innovationDescription || '—'
          }}</a-descriptions-item>
          <a-descriptions-item label="推广性说明">{{
            selectedQuestion.promotionDescription || '—'
          }}</a-descriptions-item>
          <a-descriptions-item label="数据说明">{{
            selectedQuestion.dataDescription || '—'
          }}</a-descriptions-item>
          <a-descriptions-item label="运行环境">{{
            selectedQuestion.runtimeEnvironment || '—'
          }}</a-descriptions-item>
          <a-descriptions-item label="团队分工">{{
            selectedQuestion.teamDescription || '—'
          }}</a-descriptions-item>
          <a-descriptions-item label="技术说明">{{
            selectedQuestion.technicalDescription || '—'
          }}</a-descriptions-item>
          <a-descriptions-item label="材料说明">{{
            selectedQuestion.materialDescription || '—'
          }}</a-descriptions-item>
          <a-descriptions-item label="附件" :span="1">
            截图：{{ fileListLabel(selectedQuestion.screenshotFiles) }}；视频：{{
              fileListLabel(selectedQuestion.videoFiles)
            }}；作品包：{{ fileListLabel(selectedQuestion.packageFiles) }}；补充材料：{{
              fileListLabel(selectedQuestion.supplementaryFiles)
            }}
          </a-descriptions-item>
        </a-descriptions>

        <a-divider orientation="left">AI 自评</a-divider>
        <div class="score-panel">
          <div class="score-panel-heading">
            <span>本题折算分</span>
            <strong>{{
              selectedQuestion.aiScore != null
                ? scoreText(selectedQuestion.aiScore, selectedQuestion.questionScore)
                : '未评测'
            }}</strong>
          </div>
          <div v-if="selectedQuestion.aiRawScore != null" class="raw-score-note">
            评分量表原始分：{{ selectedQuestion.aiRawScore }} /
            {{ selectedQuestion.rawScoreLimit ?? 100 }}
            <span v-if="selectedQuestion.confidence">
              · 置信度：{{ selectedQuestion.confidence }}</span
            >
            <span v-if="selectedQuestion.modelName"> · 模型：{{ selectedQuestion.modelName }}</span>
          </div>
          <div v-if="hasScores(selectedQuestion.dimensionScores)" class="dim-scores">
            <div
              v-for="(score, dimension) in selectedQuestion.dimensionScores"
              :key="dimension"
              class="dim-row"
            >
              <span class="dim-label">{{ dimension }}</span>
              <div class="dim-bar-wrap">
                <div
                  class="dim-bar"
                  :style="{ width: scorePercent(score, selectedQuestion.rawScoreLimit) }"
                />
              </div>
              <span class="dim-score">{{ score }}</span>
            </div>
          </div>
          <div v-if="selectedQuestion.conclusion" class="report-section">
            <strong>结论：</strong>{{ selectedQuestion.conclusion }}
          </div>
          <div v-if="selectedQuestion.evidence?.length" class="report-section">
            <strong>评分依据：</strong>
            <div
              v-for="(evidence, index) in selectedQuestion.evidence"
              :key="index"
              style="margin: 4px 0"
            >
              <a-tag color="blue" size="small">{{ evidence.dimension }}</a-tag
              >{{ evidence.content }}
            </div>
          </div>
          <div v-if="selectedQuestion.risks?.length" class="report-section">
            <strong>风险提示：</strong>{{ selectedQuestion.risks.join('；') }}
          </div>
          <div v-if="selectedQuestion.suggestions?.length" class="report-section">
            <strong>改进建议：</strong>{{ selectedQuestion.suggestions.join('；') }}
          </div>
        </div>

        <a-divider orientation="left">最终审核</a-divider>
        <div class="score-panel review-panel">
          <div class="score-panel-heading">
            <span>本题折算分</span>
            <strong>{{
              selectedQuestion.finalScore != null
                ? scoreText(selectedQuestion.finalScore, selectedQuestion.questionScore)
                : '未复核'
            }}</strong>
          </div>
          <div v-if="selectedQuestion.finalRawScore != null" class="raw-score-note">
            历史评分量表原始分：{{ selectedQuestion.finalRawScore }} /
            {{ selectedQuestion.rawScoreLimit ?? 100 }}
          </div>
          <div v-if="hasScores(selectedQuestion.reviewDimensionScores)" class="dim-scores">
            <div
              v-for="(score, dimension) in selectedQuestion.reviewDimensionScores"
              :key="dimension"
              class="dim-row review-dim-row"
            >
              <span class="dim-label">{{ dimension }}</span>
              <div class="dim-bar-wrap">
                <div
                  class="dim-bar"
                  :style="{ width: scorePercent(score, selectedQuestion.rawScoreLimit) }"
                />
              </div>
              <span class="dim-score">{{ score }}</span>
            </div>
          </div>
          <div class="report-section">
            <strong>复核意见：</strong>{{ selectedQuestion.reviewComment || '—' }}
          </div>
          <a-button type="primary" @click="openEdit(selectedQuestion)">复核此题</a-button>
        </div>
      </template>
    </a-modal>

    <!-- 单题复核编辑 -->
    <a-modal
      v-model:open="editVisible"
      title="生成本题最终得分"
      :confirm-loading="saving"
      @ok="submitEdit"
      ok-text="保存"
      cancel-text="取消"
    >
      <a-descriptions :column="1" size="small" style="margin-bottom: 12px">
        <a-descriptions-item label="题目"
          >第 {{ editing?.questionNo }} 题 ·
          {{ editing?.questionTitle || '未关联试题' }}</a-descriptions-item
        >
        <a-descriptions-item label="作品">{{
          editing?.workName || editing?.agentName || '—'
        }}</a-descriptions-item>
        <a-descriptions-item label="AI 自评">
          {{
            editing?.aiScore != null ? scoreText(editing.aiScore, editing.questionScore) : '未评测'
          }}
        </a-descriptions-item>
      </a-descriptions>
      <a-alert
        type="info"
        show-icon
        message="此处直接填写本题最终得分"
        description="得分范围就是本题配置的分值，保存后不再进行换算。"
        style="margin-bottom: 16px"
      />
      <a-form layout="vertical">
        <a-form-item
          :label="'最终审核得分（0 ~ ' + (editing?.questionScore ?? 100) + '）'"
          required
        >
          <a-input-number
            v-model:value="finalScore"
            :min="0"
            :max="editing?.questionScore ?? 100"
            style="width: 100%"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.reviewer-score-page {
  padding: 24px;
  background: #fff;
  border-radius: 12px;
  min-height: 100%;
}
.rsp-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.rsp-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
}
.rsp-sub {
  max-width: 720px;
  margin: 6px 0 0;
  color: #6b7280;
  font-size: 13px;
  line-height: 1.6;
}
.rsp-search {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.muted,
.question-meta,
.score-progress,
.raw-score-note {
  color: #9aa3b2;
}
.score-link {
  padding: 0;
  white-space: normal;
  text-align: left;
}
.score-progress {
  font-size: 11px;
}
.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}
.summary-item {
  display: flex;
  min-height: 78px;
  flex-direction: column;
  justify-content: center;
  padding: 12px 14px;
  border: 1px solid #edf1f7;
  border-radius: 8px;
  background: #fafcff;
}
.summary-label {
  color: #7c8798;
  font-size: 12px;
}
.summary-item strong {
  margin-top: 4px;
  color: #1f3b63;
  font-size: 18px;
}
.summary-item small {
  margin-top: 2px;
  color: #9aa3b2;
}
.question-title {
  color: #1f2937;
  font-weight: 600;
  line-height: 1.45;
}
.question-meta {
  margin-top: 3px;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.score-panel {
  padding: 14px 16px;
  border: 1px solid #e8eef8;
  border-radius: 8px;
  background: #fbfdff;
}
.review-panel {
  border-color: #e5f2e9;
  background: #fcfffd;
}
.score-panel-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  color: #536174;
}
.score-panel-heading strong {
  color: #165dba;
  font-size: 22px;
}
.review-panel .score-panel-heading strong {
  color: #2b8a52;
}
.raw-score-note {
  margin-top: 4px;
  font-size: 12px;
}
.dim-scores {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 14px 0;
}
.dim-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.dim-label {
  width: 92px;
  flex-shrink: 0;
  color: #374151;
  font-size: 13px;
}
.dim-bar-wrap {
  flex: 1;
  height: 9px;
  overflow: hidden;
  border-radius: 5px;
  background: #edf1f6;
}
.dim-bar {
  height: 100%;
  border-radius: 5px;
  background: #3b82f6;
}
.review-dim-row .dim-bar {
  background: #52a66e;
}
.dim-score {
  width: 34px;
  color: #1e40af;
  font-size: 13px;
  font-weight: 600;
  text-align: right;
}
.report-section {
  margin: 10px 0;
  color: #4b5563;
  font-size: 13px;
  line-height: 1.65;
}
.report-section strong {
  color: #374151;
}
@media (max-width: 960px) {
  .rsp-header {
    flex-direction: column;
  }
  .rsp-search {
    width: 100%;
  }
  .rsp-search :deep(.ant-input-search) {
    flex: 1;
    width: auto !important;
  }
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
