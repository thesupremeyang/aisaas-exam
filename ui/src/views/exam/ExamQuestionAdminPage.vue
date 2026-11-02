<!--
  试题管理（独立页面）

  由原「场次试题管理」页内的 L3 弹窗升级而来：
  一级菜单直达，按考试批次维护试题（题目/分值/任务场景/任务要求/提交成果/附件）。

  @author workbuddy
-->
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { message } from 'ant-design-vue'
import {
  DeleteOutlined,
  EditOutlined,
  FileTextOutlined,
  PaperClipOutlined,
  PlusOutlined,
  ReloadOutlined,
  UploadOutlined,
} from '@ant-design/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { useAccountStore } from '@/stores'
import { isPlatformAdminUsername } from '@/utils/platformAdmin'
import {
  createExamQuestion,
  deleteExamQuestion,
  listExamCampaignsForAdmin,
  listExamQuestions,
  updateExamQuestion,
  type ExamCampaign,
  type ExamQuestion,
} from '@/api/exam'
import { RouteNames } from '@/router'
import { upload as uploadFile } from '@/api/attach'
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
const questions = ref<ExamQuestion[]>([])
const questionsLoading = ref(false)

const formVisible = ref(false)
const saving = ref(false)
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
const fileUploading = ref(false)

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
  if (!start && !end) return '时间未设置，请先在「考试场次」中配置开考时间与限时'
  const fmt = (v: dayjs.Dayjs | null) => (v ? v.format('YYYY-MM-DD HH:mm') : '—')
  return `${fmt(start)} ~ ${fmt(end)}`
})

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
    await loadQuestions()
  } catch {
    message.warning('加载考试批次失败')
  } finally {
    campaignsLoading.value = false
  }
}

async function loadQuestions() {
  if (!selectedCampaignId.value) {
    questions.value = []
    return
  }
  questionsLoading.value = true
  try {
    const res = await listExamQuestions(selectedCampaignId.value)
    questions.value = res.data.data || []
  } catch {
    message.warning('加载试题失败')
  } finally {
    questionsLoading.value = false
  }
}

function handleCampaignChange() {
  void loadQuestions()
}

function resetForm() {
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
}

function openCreate() {
  if (!selectedCampaignId.value) {
    message.warning('请先选择考试批次')
    return
  }
  editingQuestionId.value = null
  resetForm()
  formVisible.value = true
}

function openEdit(q: ExamQuestion) {
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
  formVisible.value = true
}

async function saveQuestion() {
  if (!questionForm.title.trim()) {
    message.warning('请填写题目')
    return
  }
  if (!selectedCampaignId.value) return
  saving.value = true
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
      await updateExamQuestion(selectedCampaignId.value, editingQuestionId.value, payload)
      message.success('试题已更新')
    } else {
      await createExamQuestion(selectedCampaignId.value, payload)
      message.success('试题已添加')
    }
    formVisible.value = false
    await loadQuestions()
  } catch {
    message.warning('保存试题失败')
  } finally {
    saving.value = false
  }
}

async function removeQuestion(q: ExamQuestion) {
  if (!selectedCampaignId.value || !q.id) return
  try {
    await deleteExamQuestion(selectedCampaignId.value, q.id)
    message.success('试题已删除')
    await loadQuestions()
  } catch {
    message.warning('删除试题失败')
  }
}

function formatFileSize(bytes: number | null) {
  if (!bytes) return ''
  const mb = bytes / 1024 / 1024
  if (mb >= 1) return `${mb.toFixed(1)} MB`
  return `${Math.round(bytes / 1024)} KB`
}

async function handleFileUpload(options: { file: File }) {
  const file = options.file
  fileUploading.value = true
  try {
    const res = await uploadFile(file)
    const fileId = res.data.data
    questionForm.attachmentUrl = fileId
    questionForm.attachmentName = file.name
    questionForm.attachmentSize = file.size
    message.success('附件上传成功')
  } catch {
    message.warning('附件上传失败')
  } finally {
    fileUploading.value = false
  }
}

function removeAttachment() {
  questionForm.attachmentUrl = ''
  questionForm.attachmentName = ''
  questionForm.attachmentSize = null
}

onMounted(loadCampaigns)
</script>

<template>
  <main class="question-admin-page">
    <header class="page-heading">
      <div class="title-mark"><FileTextOutlined /></div>
      <div class="heading-copy">
        <span>考试管理</span>
        <h1>试题管理</h1>
        <p>选择考试批次，维护该场考试的题目、分值与任务说明。</p>
      </div>
      <div class="heading-actions">
        <a-button type="primary" :disabled="!selectedCampaignId" @click="openCreate">
          <template #icon><PlusOutlined /></template>
          添加试题
        </a-button>
        <a-button :loading="questionsLoading" :disabled="!selectedCampaignId" @click="loadQuestions">
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
        <a-select-option v-for="item in campaigns" :key="item.id" :value="String(item.id)">
          {{ item.name || '未命名考试' }}
        </a-select-option>
      </a-select>
      <div v-if="selectedCampaign" class="window-summary">
        <a-tag :color="selectedState ? campaignStateColor(selectedState) : 'default'">
          {{ selectedState ? campaignStateLabel(selectedState) : '—' }}
        </a-tag>
        <span>{{ timeWindowLabel }}</span>
      </div>
      <div class="toolbar-hint">
        还没有场次？先到
        <a @click="router.push({ name: RouteNames.EXAM_ADMIN })">考试场次</a>
        新建一场考试，再回到这里出题。
      </div>
    </section>

    <a-card :bordered="false" class="question-card" v-if="selectedCampaignId">
      <a-table
        :data-source="questions"
        :loading="questionsLoading"
        :pagination="false"
        row-key="id"
        :scroll="{ x: 1100 }"
      >
        <a-table-column title="序号" key="index" :width="60">
          <template #default="{ index }">{{ index + 1 }}</template>
        </a-table-column>
        <a-table-column title="题目" data-index="title" :width="280">
          <template #default="{ record }">
            <div class="question-title">{{ record.title }}</div>
            <div v-if="record.attachmentName" class="question-attach">
              <PaperClipOutlined /> {{ record.attachmentName }}
              <small v-if="record.attachmentSize">（{{ formatFileSize(record.attachmentSize) }}）</small>
            </div>
          </template>
        </a-table-column>
        <a-table-column title="分值" data-index="score" :width="70" />
        <a-table-column title="建议用时" key="suggestedMinutes" :width="100">
          <template #default="{ record }">{{ record.suggestedMinutes != null ? record.suggestedMinutes + ' 分钟' : '—' }}</template>
        </a-table-column>
        <a-table-column title="任务场景" data-index="taskScenario" :width="220" show-overflow-tooltip />
        <a-table-column title="任务要求" data-index="taskRequirement" :width="220" show-overflow-tooltip />
        <a-table-column title="提交成果" data-index="submissionDeliverable" :width="220" show-overflow-tooltip />
        <a-table-column title="操作" key="action" :width="130">
          <template #default="{ record }">
            <a-button type="link" size="small" @click="openEdit(record as ExamQuestion)">
              <EditOutlined /> 编辑
            </a-button>
            <a-popconfirm title="确认删除该试题？" ok-text="删除" cancel-text="取消" @confirm="removeQuestion(record as ExamQuestion)">
              <a-button type="link" size="small" danger>
                <DeleteOutlined /> 删除
              </a-button>
            </a-popconfirm>
          </template>
        </a-table-column>
        <template #emptyText>
          <a-empty description="该批次暂无试题，点击右上角「添加试题」开始出题" />
        </template>
      </a-table>
    </a-card>

    <a-empty v-else-if="!campaignsLoading" description="请选择一个考试批次" class="empty-state" />

    <a-modal
      v-model:open="formVisible"
      :title="editingQuestionId ? '编辑试题' : '添加试题'"
      :confirm-loading="saving"
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
              <a-button type="link" size="small" danger @click="removeAttachment">删除</a-button>
            </div>
          </template>
          <template v-else>
            <a-upload :show-upload-list="false" :custom-request="handleFileUpload" accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.zip,.rar,.7z,.txt,.png,.jpg,.jpeg">
              <a-button :loading="fileUploading">
                <template #icon><UploadOutlined /></template>
                上传附件
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
.question-admin-page {
  min-height: 100%;
  padding: 28px;
  color: #1d2a3f;
  background: #f5f8fc;
}

.page-heading,
.toolbar-card,
.question-card {
  max-width: 1560px;
  margin-right: auto;
  margin-left: auto;
}

.page-heading,
.toolbar-card,
.question-card {
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
  background: #d97706;
}

.heading-copy {
  flex: 1;
}

.heading-copy > span {
  color: #d97706;
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
  margin-bottom: 18px;
  padding: 18px 24px;
  border-radius: 8px;
}

.toolbar-label {
  margin-bottom: 8px;
  color: #718096;
  font-size: 12px;
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

.toolbar-hint {
  margin-top: 10px;
  color: #8a98ad;
  font-size: 12px;
}

.toolbar-hint a {
  color: #2f73d9;
  cursor: pointer;
}

.question-card {
  overflow: hidden;
  border-radius: 8px;
}

.question-title {
  color: #1d2a3f;
  font-weight: 500;
}

.question-attach {
  margin-top: 3px;
  color: #8a98ad;
  font-size: 12px;
}

.empty-state {
  margin-top: 80px;
}

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

@media (max-width: 760px) {
  .question-admin-page {
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
}
</style>
