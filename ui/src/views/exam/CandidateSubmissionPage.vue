<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { useExamStore } from '@/stores'
import {
  getCandidateAgentConfiguration,
  listCandidateAgents,
  listSubmissions,
  type ExamAgentSummaryVO,
  type ExamSubmission,
} from '@/api/exam'
import { getChatKey } from '@/api/agentChatKey'
import { upload as uploadAttach } from '@/api/attach'
import {
  UploadOutlined,
  FileOutlined,
  CloseCircleFilled,
  PictureOutlined,
  CodeOutlined,
  FileAddOutlined,
  RightOutlined,
  ExclamationCircleFilled,
} from '@ant-design/icons-vue'
import { decodeSubmissionForm, emptySubmissionForm, type MaterialFile, type SubmissionForm } from './form'
import { currentExamRubric } from './rubric'
import { buildCommunicationUrl } from '@/utils/communication'
import type { AgentDefinitionVO } from '@/types'

const props = defineProps<{ submissionId: string }>()
const router = useRouter()
const examStore = useExamStore()
const currentStep = ref(1)
const form = ref(emptySubmissionForm())
const agentId = ref<string>('')
const agentName = ref<string>('')
const agentSelectorOpen = ref(false)
const agentSelectorLoading = ref(false)
const candidateAgents = ref<ExamAgentSummaryVO[]>([])
const usedAgentIds = ref<Set<string>>(new Set())
const agentConfigVisible = ref(false)
const agentConfigLoading = ref(false)
const agentConfig = ref<AgentDefinitionVO | null>(null)
function isAgentUsed(agent: ExamAgentSummaryVO): boolean {
  return usedAgentIds.value.has(String(agent.id))
}
const rubric = currentExamRubric()

const submitted = computed(() => !!examStore.submission && examStore.submission.status !== 'DRAFT')
const examExpired = ref(false)
const examState = computed(() => examStore.submission?.examState)
const readOnly = computed(
  () => !examStore.submission || submitted.value || examExpired.value || examState.value !== 'OPEN',
)
const scoreQueryMode = computed(() => sessionStorage.getItem('exam:score-query-mode') === '1')
const statusText = computed(
  () =>
    ({
      DRAFT: '草稿',
      AI_QUEUED: 'AI 智评排队中',
      REVIEW_PENDING: '等待评委复核',
      PUBLISHED: '成绩已发布',
      TIMEOUT_SUBMITTED: '时间到，已自动交卷',
    })[examStore.submission?.status || 'DRAFT'] ||
    examStore.submission?.status ||
    '草稿',
)

let autosaveTimer: number | undefined
let lastSavedFingerprint = ''
let savePromise: Promise<boolean> | null = null

function draftFingerprint(): string {
  return JSON.stringify({
    agentId: agentId.value || '',
    formJson: JSON.stringify(form.value),
    currentStep: currentStep.value,
  })
}

function hydrate(submission: ExamSubmission | null) {
  if (!submission) return
  form.value = decodeSubmissionForm(submission.formJson)
  agentId.value = submission.agentId || ''
  agentName.value = submission.agentName || ''
  if (!form.value.materials.demoUrl?.trim() && agentId.value) {
    void fillDemoUrlFromAgent(agentId.value)
  }
  currentStep.value = Math.min(Math.max(submission.currentStep || 1, 1), 4)
  examExpired.value = submission.examState === 'ENDED'
  lastSavedFingerprint = draftFingerprint()
}

function toDraft(): ExamSubmission {
  return {
    id: props.submissionId,
    agentId: agentId.value || undefined,
    formJson: JSON.stringify(form.value),
    currentStep: currentStep.value,
  }
}

async function load() {
  try {
    hydrate(await examStore.loadSubmission(props.submissionId))
  } catch {
    message.error('无法加载作品信息，请稍后重试')
  }
}

async function saveDraft(showMessage = true): Promise<boolean> {
  if (readOnly.value) return false
  if (savePromise) await savePromise
  if (readOnly.value) return false
  const fingerprint = draftFingerprint()
  if (!showMessage && fingerprint === lastSavedFingerprint) return true

  const operation: Promise<boolean> = (async () => {
    try {
      await examStore.saveDraft(props.submissionId, toDraft())
      lastSavedFingerprint = fingerprint
      if (showMessage) message.success('作品草稿已保存')
      return true
    } catch {
      message.error('保存失败，请稍后重试')
      return false
    }
  })()
  savePromise = operation
  try {
    return await operation
  } finally {
    if (savePromise === operation) savePromise = null
  }
}

async function openAgentSelector() {
  if (readOnly.value) return
  const campaignId = sessionStorage.getItem('exam:campaignId')
  if (!campaignId) {
    message.warning('未获取到考试批次，请返回考试门户后重试')
    return
  }
  agentSelectorOpen.value = true
  agentSelectorLoading.value = true
  try {
    const response = await listCandidateAgents(campaignId)
    candidateAgents.value = response.data.data || []
    const eid = examStore.submission?.enrollmentId
    if (eid) {
      try {
        const subRes = await listSubmissions(eid)
        const used = new Set<string>()
        ;(subRes.data?.data || []).forEach((s: ExamSubmission) => {
          if (s.id !== props.submissionId && s.agentId) used.add(String(s.agentId))
        })
        usedAgentIds.value = used
      } catch {
        usedAgentIds.value = new Set()
      }
    }
  } catch {
    message.error('加载我的智能体失败，请稍后重试')
  } finally {
    agentSelectorLoading.value = false
  }
}

async function openAgentConfiguration() {
  if (!readOnly.value || !scoreQueryMode.value || !agentId.value) return
  agentConfigVisible.value = true
  agentConfigLoading.value = true
  try {
    const response = await getCandidateAgentConfiguration(props.submissionId)
    agentConfig.value = response.data.data || null
  } catch {
    agentConfigVisible.value = false
    message.error('无法加载智能体配置，请稍后重试')
  } finally {
    agentConfigLoading.value = false
  }
}

function selectAgent(agent: ExamAgentSummaryVO) {
  if (readOnly.value) return
  if (isAgentUsed(agent)) {
    message.warning('该智能体已在本批次提交过，请勿重复选择')
    return
  }
  agentId.value = String(agent.id ?? '')
  agentName.value = agent.name ?? ''
  agentSelectorOpen.value = false
  void fillDemoUrlFromAgent(agentId.value)
  void saveDraft(false)
}

/**
 * 根据关联智能体自动获取「外置对话入口」并填入演示链接 / 可访问地址。
 * 生成规则与智能体 API 配置页（AgentConfigApiDoc）一致，并包含当前应用上下文路径。
 */
async function fillDemoUrlFromAgent(id: string) {
  if (!id) return
  try {
    const res = await getChatKey(id, false)
    const key = res.data?.data
    if (key) {
      form.value.materials.demoUrl = buildCommunicationUrl(key)
    }
  } catch {
    // 获取失败不影响表单，用户仍可手动填写演示链接
  }
}

async function next() {
  if (readOnly.value) {
    if (currentStep.value < 4) currentStep.value += 1
    return
  }
  if (currentStep.value === 1) {
    const { name, identityNumber, phone } = form.value.applicant
    if (!name?.trim()) {
      message.warning('请填写姓名')
      return
    }
    if (!identityNumber?.trim()) {
      message.warning('请填写身份证号')
      return
    }
    if (!phone?.trim()) {
      message.warning('请填写联系电话')
      return
    }
  }
  if (currentStep.value === 2) {
    const work = form.value.work
    if (!work.title?.trim()) {
      message.warning('请填写作品名称')
      return
    }
    if (!work.scene?.trim()) {
      message.warning('请填写应用场景')
      return
    }
    if (!work.functionDescription?.trim()) {
      message.warning('请填写功能说明')
      return
    }
    if (!agentId.value) {
      message.warning('请选择平台内智能体')
      return
    }
  }
  if (currentStep.value === 3) {
    if (!form.value.materials.demoUrl?.trim()) {
      message.warning('请填写演示链接 / 可访问地址')
      return
    }
  }
  if (currentStep.value < 4) {
    currentStep.value += 1
    await saveDraft(false)
  }
}

function previous() {
  if (currentStep.value > 1) currentStep.value -= 1
}

/** 点击步骤标签直接跳转（只读时仅查看，字段已禁用不可改） */
function onStepClick(index: number) {
  currentStep.value = index + 1
}

const uploading = ref<Record<string, boolean>>({})
const materialRules: Record<keyof SubmissionForm['materials'], { maxCount: number; maxSize: number }> = {
  demoUrl: { maxCount: 0, maxSize: 0 },
  videoFiles: { maxCount: 1, maxSize: 10 * 1024 * 1024 },
  screenshotFiles: { maxCount: 8, maxSize: 1 * 1024 * 1024 },
  packageFiles: { maxCount: 1, maxSize: 10 * 1024 * 1024 },
  supplementaryFiles: { maxCount: 2, maxSize: 5 * 1024 * 1024 },
}

async function uploadMaterial(kind: 'videoFiles' | 'screenshotFiles' | 'packageFiles' | 'supplementaryFiles', files: File[]) {
  if (readOnly.value) return
  const rule = materialRules[kind]
  const current = form.value.materials[kind] as MaterialFile[]
  const pending = files.filter((file) => {
    if (file.size > rule.maxSize) {
      message.warning(`「${file.name}」超过 ${Math.round(rule.maxSize / 1024 / 1024)}MB，已跳过`)
      return false
    }
    return true
  })
  if (current.length + pending.length > rule.maxCount) {
    message.warning(`最多上传 ${rule.maxCount} 个文件`)
    return
  }
  if (pending.length === 0) return
  uploading.value[kind] = true
  try {
    for (const file of pending) {
      const response = await uploadAttach(file)
      current.push({ id: response.data.data, name: file.name, size: file.size })
    }
    await saveDraft(false)
  } catch {
    message.error('文件上传失败，请稍后重试')
  } finally {
    uploading.value[kind] = false
  }
}

/** a-upload 上传回调：单文件逐次走同一逻辑 */
function doUpload(kind: 'videoFiles' | 'screenshotFiles' | 'packageFiles' | 'supplementaryFiles', options: { file: File }) {
  uploadMaterial(kind, [options.file])
}

/** 上传前校验：数量已满则拦截，避免无效请求 */
function beforeUpload(kind: 'videoFiles' | 'screenshotFiles' | 'packageFiles' | 'supplementaryFiles') {
  const rule = materialRules[kind]
  if (form.value.materials[kind].length >= rule.maxCount) {
    message.warning(`最多上传 ${rule.maxCount} 个文件`)
    return false
  }
  return true
}

function formatSize(bytes: number) {
  if (!bytes) return ''
  const mb = bytes / 1024 / 1024
  if (mb >= 1) return `${mb.toFixed(1)} MB`
  return `${Math.round(bytes / 1024)} KB`
}

const uploadConfigs = [
  { field: 'screenshotFiles', title: '系统截图', hint: '最多 8 张 · 单张 ≤ 1MB', accept: 'image/png,image/jpeg,image/webp', icon: 'image' },
  { field: 'packageFiles', title: '代码 / 技能包', hint: '最多 1 个 · 单文件 ≤ 10MB（.zip）', accept: '.zip', icon: 'code' },
  { field: 'supplementaryFiles', title: '补充材料', hint: '最多 2 个 · 单文件 ≤ 5MB', accept: '', icon: 'file' },
] as const

function removeMaterial(kind: 'videoFiles' | 'screenshotFiles' | 'packageFiles' | 'supplementaryFiles', id: string) {
  if (readOnly.value) return
  form.value.materials[kind] = form.value.materials[kind].filter((file) => file.id !== id)
  saveDraft(false)
}

async function submit() {
  if (readOnly.value) return
  const { name, identityNumber, phone } = form.value.applicant
  const work = form.value.work
  // 身份核验：姓名 / 身份证号 / 电话
  if (!name?.trim()) {
    message.warning('请填写身份核验中的「姓名」')
    currentStep.value = 1
    return
  }
  if (!identityNumber?.trim()) {
    message.warning('请填写身份核验中的「身份证号」')
    currentStep.value = 1
    return
  }
  if (!phone?.trim()) {
    message.warning('请填写身份核验中的「联系电话」')
    currentStep.value = 1
    return
  }
  // 作品基础信息
  if (!work.title?.trim()) {
    message.warning('请填写作品名称')
    currentStep.value = 2
    return
  }
  if (!work.scene?.trim()) {
    message.warning('请填写应用场景')
    currentStep.value = 2
    return
  }
  if (!work.functionDescription?.trim()) {
    message.warning('请填写功能说明')
    currentStep.value = 2
    return
  }
  // 平台内智能体
  if (!agentId.value) {
    message.warning('请选择平台内智能体')
    currentStep.value = 2
    return
  }
  // 材料说明：演示链接必填
  if (!form.value.materials.demoUrl?.trim()) {
    message.warning('请填写演示链接 / 可访问地址')
    currentStep.value = 3
    return
  }
  // 原创声明
  if (!form.value.declarationAccepted) {
    message.warning('请先确认原创声明')
    currentStep.value = 4
    return
  }
  try {
    const saved = await saveDraft(false)
    if (!saved) return
    await examStore.submitWork(props.submissionId)
    message.success('作品已提交，正在生成 AI 智评报告')
    await router.push({ name: 'CandidateAssessment', params: { submissionId: props.submissionId } })
  } catch {
    message.error('提交失败，请检查信息后重试')
  }
}

function handleExamExpired() {
  examExpired.value = true
  void load()
}

function handleExamWindowUpdated() {
  void load()
}

watch(
  [form, agentId, currentStep],
  () => {
    if (readOnly.value) return
    if (autosaveTimer) window.clearTimeout(autosaveTimer)
    autosaveTimer = window.setTimeout(() => void saveDraft(false), 1000)
  },
  { deep: true },
)

watch(() => props.submissionId, load)
onMounted(() => {
  window.addEventListener('exam:expired', handleExamExpired)
  window.addEventListener('exam:window-state-updated', handleExamWindowUpdated)
  void load()
})
onBeforeUnmount(() => {
  window.removeEventListener('exam:expired', handleExamExpired)
  window.removeEventListener('exam:window-state-updated', handleExamWindowUpdated)
  if (autosaveTimer) window.clearTimeout(autosaveTimer)
})
sessionStorage.setItem('exam:submissionId', props.submissionId)
</script>

<template>
  <main class="exam-page">
    <header class="page-header">
      <div>
        <h1>提交你的智能体作品</h1>
        <p>依次补充作品信息与材料；AI 智评只读取本页提交内容和受控的智能体摘要。</p>
      </div>
      <a-tag color="blue" class="status-tag">{{ statusText }}</a-tag>
    </header>

    <a-alert
      v-if="submitted"
      :type="examStore.submission?.status === 'TIMEOUT_SUBMITTED' ? 'warning' : 'info'"
      show-icon
      :message="examStore.submission?.status === 'TIMEOUT_SUBMITTED' ? '考试时间到，系统已自动交卷。' : '作品已提交，当前内容已进入评测流程。'"
      class="submitted-alert"
    >
      <template #action>
        <a-button
          type="link"
          @click="router.push({ name: 'CandidateAssessment', params: { submissionId } })"
          >查看 AI 智评</a-button
        >
      </template>
    </a-alert>

    <a-alert
      v-if="!submitted && examState === 'WAITING'"
      type="info"
      show-icon
      message="考试尚未开始，当前页面暂不可编辑。"
      :description="`开考时间：${examStore.submission?.examStartAt || '待定'}`"
      class="submitted-alert"
    />

    <a-alert
      v-else-if="!submitted && readOnly && examState === 'ENDED'"
      type="warning"
      show-icon
      message="考试时间已结束，系统正在处理自动交卷。"
      class="submitted-alert"
    />

    <a-steps :current="currentStep - 1" class="submission-steps">
      <a-step title="身份核验" description="确认参赛信息" class="step-clickable" @click="onStepClick(0)" />
      <a-step title="作品信息" description="选择并说明智能体" class="step-clickable" @click="onStepClick(1)" />
      <a-step title="材料说明" description="填写可验证材料" class="step-clickable" @click="onStepClick(2)" />
      <a-step title="确认提交" description="开始 AI 智评" class="step-clickable" @click="onStepClick(3)" />
    </a-steps>

    <section class="content-grid" :class="{ 'is-readonly': readOnly }">
      <a-card :loading="examStore.loading" class="form-card">
        <section v-show="currentStep === 1">
          <h2>身份核验</h2>
          <p class="section-description">请核对本次考试作品的提交人和所属单位。</p>
          <div class="two-column">
            <a-form-item label="姓名" required
              ><a-input v-model:value="form.applicant.name" :disabled="readOnly" placeholder="请输入真实姓名"
            /></a-form-item>
            <a-form-item label="身份证号" required
              ><a-input v-model:value="form.applicant.identityNumber" :disabled="readOnly" placeholder="请输入身份证号"
            /></a-form-item>
            <a-form-item label="联系电话" required
              ><a-input v-model:value="form.applicant.phone" :disabled="readOnly" placeholder="请输入联系电话"
            /></a-form-item>
            <a-form-item label="所属单位 / 团队"
              ><a-input v-model:value="form.applicant.organization" :disabled="readOnly"
            /></a-form-item>
          </div>
        </section>

        <section v-show="currentStep === 2">
          <h2>智能体作品信息</h2>
          <p class="section-description">
            仅提交你在平台内创建的智能体，提交后将显示智能体名称。
          </p>

          <!-- 智能体选择卡片 -->
          <div
            class="agent-select-card"
            :class="{ selected: !!agentId, disabled: readOnly }"
            @click="!readOnly && openAgentSelector()"
          >
            <span class="agent-select-icon"><bot-outlined /></span>
            <div class="agent-select-body">
              <span class="agent-select-title">
                {{ agentId ? `已选择智能体：${agentName || '已关联智能体'}` : '选择平台内智能体' }}
              </span>
              <span class="agent-select-sub">
                {{ agentId ? (agentName ? '已关联' : '已关联智能体') : '点击此处从「我的智能体」中挑选作品' }}
              </span>
            </div>
            <a-tag v-if="agentId" color="blue" class="agent-select-tag">已关联</a-tag>
            <span v-else class="agent-select-action"><right-outlined /></span>
          </div>
          <a-button
            v-if="readOnly && scoreQueryMode && agentId"
            type="link"
            size="small"
            class="agent-config-link"
            @click.stop="openAgentConfiguration"
          >
            查看智能体配置
          </a-button>

          <!-- 基础信息 -->
          <div class="form-group">
            <div class="form-group-title"><span class="dot" />基础信息</div>
            <a-form-item label="作品名称" required
              ><a-input v-model:value="form.work.title" :disabled="readOnly" placeholder="为你的智能体作品起一个清晰的名称"
            /></a-form-item>
            <a-form-item label="应用场景" required
              ><a-textarea v-model:value="form.work.scene" :rows="3" :disabled="readOnly" placeholder="描述该智能体主要解决什么场景的问题"
            /></a-form-item>
            <a-form-item label="功能说明" required
              ><a-textarea
                v-model:value="form.work.functionDescription"
                :rows="4"
                :disabled="readOnly"
                placeholder="概述核心功能与交互方式"
            /></a-form-item>
          </div>

          <!-- 能力说明 -->
          <div class="form-group">
            <div class="form-group-title"><span class="dot dot-green" />能力说明</div>
            <div class="one-column">
              <a-form-item label="创新性说明"
                ><a-textarea
                  v-model:value="form.work.innovationDescription"
                  :rows="4"
                  :disabled="readOnly"
                  placeholder="相较同类方案的创新点"
              /></a-form-item>
              <a-form-item label="推广性说明"
                ><a-textarea
                  v-model:value="form.work.promotionDescription"
                  :rows="4"
                  :disabled="readOnly"
                  placeholder="可复用、可推广的场景与价值"
              /></a-form-item>
              <a-form-item label="数据说明"
                ><a-textarea
                  v-model:value="form.work.dataDescription"
                  :rows="4"
                  :disabled="readOnly"
                  placeholder="涉及的数据来源与处理"
              /></a-form-item>
            </div>
          </div>

          <!-- 技术说明 -->
          <div class="form-group">
            <div class="form-group-title"><span class="dot dot-purple" />技术说明</div>
            <a-form-item label="技术说明"
              ><a-textarea
                v-model:value="form.work.technicalDescription"
                :rows="4"
                :disabled="readOnly"
                placeholder="关键技术方案与实现要点"
            /></a-form-item>
          </div>

          <!-- 特性标签 -->
          <div class="form-group">
            <div class="form-group-title"><span class="dot dot-orange" />作品特性</div>
            <div class="feature-chips">
              <label class="feature-chip" :class="{ active: form.work.hasSkill, disabled: readOnly }">
                <a-checkbox v-model:checked="form.work.hasSkill" :disabled="readOnly" class="chip-check" />
                <span class="chip-text">包含 Skill</span>
              </label>
              <label class="feature-chip" :class="{ active: form.work.hasMcp, disabled: readOnly }">
                <a-checkbox v-model:checked="form.work.hasMcp" :disabled="readOnly" class="chip-check" />
                <span class="chip-text">使用 MCP</span>
              </label>
              <label class="feature-chip" :class="{ active: form.work.hasKnowledge, disabled: readOnly }">
                <a-checkbox v-model:checked="form.work.hasKnowledge" :disabled="readOnly" class="chip-check" />
                <span class="chip-text">使用知识库</span>
              </label>
              <label class="feature-chip" :class="{ active: form.work.hasDemo, disabled: readOnly }">
                <a-checkbox v-model:checked="form.work.hasDemo" :disabled="readOnly" class="chip-check" />
                <span class="chip-text">有可验证演示</span>
              </label>
            </div>
          </div>
        </section>

        <section v-show="currentStep === 3">
          <h2>材料说明</h2>
          <p class="section-description">请说明已上传或已备案材料。AI 不会执行外部演示链接。</p>
          <a-form-item label="演示链接 / 可访问地址" required
            ><a-input
              v-model:value="form.materials.demoUrl"
              placeholder="关联智能体后自动填充，也可手动修改 https://"
              :disabled="readOnly"
            />
            <template #extra>
              <span v-if="agentId">已根据关联智能体的「外置对话入口」自动填充，可手动修改。</span>
              <span v-else>请先在「智能体作品信息」中关联平台内智能体，将自动获取其外置对话入口。</span>
            </template>
          </a-form-item>

          <div class="material-upload-list">
            <div v-for="cfg in uploadConfigs" :key="cfg.field" class="upload-card">
              <div class="upload-card-head">
                <span class="upload-card-icon" :class="`icon-${cfg.icon}`">
                  <picture-outlined v-if="cfg.icon === 'image'" />
                  <code-outlined v-else-if="cfg.icon === 'code'" />
                  <file-add-outlined v-else />
                </span>
                <div class="upload-card-meta">
                  <span class="upload-card-title">{{ cfg.title }}</span>
                  <span class="upload-card-hint">{{ cfg.hint }}</span>
                </div>
                <a-upload
                  :multiple="cfg.field !== 'packageFiles'"
                  :accept="cfg.accept"
                  :show-upload-list="false"
                  :disabled="readOnly || uploading[cfg.field]"
                  :before-upload="() => beforeUpload(cfg.field)"
                  :custom-request="(o: any) => doUpload(cfg.field, o)"
                >
                  <a-button
                    type="primary"
                    ghost
                    :disabled="readOnly || uploading[cfg.field]"
                    :loading="uploading[cfg.field]"
                  >
                    <template #icon><upload-outlined /></template>
                    {{ uploading[cfg.field] ? '上传中…' : '上传文件' }}
                  </a-button>
                </a-upload>
              </div>

              <div v-if="form.materials[cfg.field].length" class="upload-file-list">
                <div v-for="file in form.materials[cfg.field]" :key="file.id" class="upload-file-item">
                  <file-outlined class="upload-file-ico" />
                  <span class="upload-file-name" :title="file.name">{{ file.name }}</span>
                  <span v-if="file.size" class="upload-file-size">{{ formatSize(file.size) }}</span>
                  <close-circle-filled
                    v-if="!readOnly"
                    class="upload-file-del"
                    @click="removeMaterial(cfg.field, file.id)"
                  />
                </div>
              </div>
              <div v-else class="upload-empty">尚未上传，点击右侧按钮选择文件</div>
            </div>
          </div>
        </section>

        <section v-show="currentStep === 4">
          <h2>确认提交</h2>
          <a-descriptions bordered :column="1" size="small">
            <a-descriptions-item label="关联智能体">{{
              agentName || (agentId ? '已关联智能体' : '未填写')
            }}</a-descriptions-item>
            <a-descriptions-item label="作品名称">{{
              form.work.title || '未填写'
            }}</a-descriptions-item>
            <a-descriptions-item label="提交材料"
              >{{
                [
                  ...form.materials.videoFiles,
                  ...form.materials.screenshotFiles,
                  ...form.materials.packageFiles,
                  ...form.materials.supplementaryFiles,
                ].length
              }}
              个文件已上传</a-descriptions-item
            >
            <a-descriptions-item label="提交人">{{
              form.applicant.name || '未填写'
            }}</a-descriptions-item>
            <a-descriptions-item label="联系电话">{{
              form.applicant.phone || '未填写'
            }}</a-descriptions-item>
          </a-descriptions>
          <a-checkbox
            v-model:checked="form.declarationAccepted"
            class="declaration"
            :disabled="readOnly"
          >
            本人确认本次提交的作品为原创。
          </a-checkbox>
        </section>

        <div class="actions">
          <a-button :disabled="currentStep === 1" @click="previous">上一步</a-button>
          <a-space>
            <a-button v-if="!readOnly" :loading="examStore.saving" @click="saveDraft()">保存草稿</a-button>
            <a-button v-if="currentStep < 4" type="primary" @click="next">下一步</a-button>
            <a-button v-if="!readOnly && currentStep === 4" type="primary" :loading="examStore.submitting" :disabled="examStore.saving || !agentId" @click="submit"
              >提交并开始 AI 智评</a-button
            >
          </a-space>
        </div>
      </a-card>
      <aside class="submission-reminder" aria-label="重要提交提醒">
        <div class="submission-reminder-head">
          <span class="submission-reminder-icon"><exclamation-circle-filled /></span>
          <h2>重要提交提醒</h2>
        </div>
        <p class="submission-reminder-lead">
          本页面提交的所有内容，必须基于你所关联智能体的实际配置和真实完成情况填写。
        </p>
        <ul class="submission-reminder-list">
          <li>请确保作品说明、演示地址和上传材料均与该智能体的实际能力一致。</li>
          <li>不得提交智能体未实现、未配置或与实际效果不符的功能。</li>
        </ul>
        <p class="submission-reminder-consequence">
          如提交内容脱离智能体实际配置、夸大或虚构功能，可能导致 AI 智评得分降低；情节严重的，可能取消本次考试成绩。
        </p>
      </aside>
    </section>

    <a-modal v-model:open="agentSelectorOpen" title="我的智能体" :footer="null" width="720px">
      <a-spin v-if="agentSelectorLoading" />
      <a-empty v-else-if="!candidateAgents.length" description="暂无可提交的智能体，请先去制作智能体" />
      <a-list v-else :data-source="candidateAgents" item-layout="horizontal">
        <template #renderItem="{ item }">
          <a-list-item>
            <a-list-item-meta :title="item.name" :description="item.description" />
            <template #actions>
              <a-tag v-if="isAgentUsed(item)" color="default">已提交</a-tag>
              <a-button v-else type="link" @click="selectAgent(item)">选择</a-button>
            </template>
          </a-list-item>
        </template>
      </a-list>
    </a-modal>

    <a-modal
      v-model:open="agentConfigVisible"
      :title="agentConfig?.name ? `${agentConfig.name} — 智能体配置` : '智能体配置'"
      :footer="null"
      width="820px"
      destroy-on-close
    >
      <a-spin :spinning="agentConfigLoading">
        <a-descriptions v-if="agentConfig" bordered :column="1" size="small">
          <a-descriptions-item label="智能体名称">{{ agentConfig.name || '未设置' }}</a-descriptions-item>
          <a-descriptions-item label="智能体描述">{{ agentConfig.description || '未设置' }}</a-descriptions-item>
          <a-descriptions-item label="系统提示词">
            <pre class="agent-prompt">{{ agentConfig.systemPrompt || '未配置自定义系统提示词' }}</pre>
          </a-descriptions-item>
          <a-descriptions-item label="Skill">
            {{ agentConfig.skill?.length ? agentConfig.skill.join('、') : '未配置' }}
          </a-descriptions-item>
          <a-descriptions-item label="知识库">
            {{ agentConfig.knowledgeBase?.length ? agentConfig.knowledgeBase.join('、') : '未配置' }}
          </a-descriptions-item>
          <a-descriptions-item label="工具 / MCP">
            工具 {{ agentConfig.tool?.length || 0 }} 项，MCP {{ agentConfig.mcp?.length || 0 }} 项
          </a-descriptions-item>
        </a-descriptions>
      </a-spin>
      <p class="agent-config-readonly-note">该配置为考试结束时关联智能体的查看信息，仅供回看，不能修改。</p>
    </a-modal>
  </main>
</template>

<style scoped lang="scss">
.exam-page {
  max-width: 1180px;
  margin: 0 auto;
  padding: 32px 24px 56px;
  color: #14213d;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 24px;
  margin-bottom: 24px;
}
.page-header h1 {
  margin: 4px 0 8px;
  font-size: 30px;
}
.page-header p {
  margin: 0;
  color: #62728b;
}
.eyebrow {
  margin: 0;
  color: #1677ff;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.status-tag {
  margin-top: 10px;
  padding: 4px 10px;
}
.submitted-alert {
  margin-bottom: 20px;
}
.submission-steps {
  padding: 20px 24px;
  margin-bottom: 20px;
  background: linear-gradient(135deg, #f5f9ff, #eef6ff);
  border: 1px solid #d6e8ff;
  border-radius: 12px;
}
.submission-steps :deep(.ant-steps-item.step-clickable) {
  cursor: pointer;
}
.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 20px;
  align-items: start;
}
.form-card {
  min-height: 520px;
}
.form-card h2 {
  margin: 0 0 6px;
  font-size: 20px;
}
.section-description {
  margin: 0 0 24px;
  color: #71829d;
}

/* ===== 作品信息美化 ===== */
.agent-select-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px 20px;
  margin-bottom: 26px;
  border: 1.5px dashed #c5d6ee;
  border-radius: 14px;
  background: linear-gradient(135deg, #f4f9ff, #eef4ff);
  cursor: pointer;
  transition: border-color 0.18s, box-shadow 0.18s, transform 0.12s;
}
.agent-select-card:hover {
  border-color: #4f8bff;
  box-shadow: 0 6px 18px rgba(22, 101, 216, 0.12);
}
.agent-select-card.selected {
  border-style: solid;
  border-color: #4f8bff;
  background: linear-gradient(135deg, #eef5ff, #e7f0ff);
}
.agent-select-card.disabled {
  cursor: not-allowed;
  opacity: 0.8;
}
.agent-select-icon {
  flex: none;
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  border-radius: 12px;
  font-size: 22px;
  color: #fff;
  background: linear-gradient(135deg, #4f8bff, #2f6bd8);
}
.agent-select-body {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}
.agent-select-title {
  font-size: 15px;
  font-weight: 600;
  color: #0a1e45;
}
.agent-select-sub {
  font-size: 13px;
  color: #8a98ad;
  margin-top: 3px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.agent-select-tag {
  flex: none;
}
.agent-select-action {
  flex: none;
  color: #4f8bff;
  font-size: 16px;
}
.agent-config-link {
  margin: -18px 0 22px;
  padding-left: 0;
}
.agent-prompt {
  max-height: 280px;
  margin: 0;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  color: #394967;
  font: inherit;
  line-height: 1.7;
}
.agent-config-readonly-note {
  margin: 16px 0 0;
  color: #7a88a0;
  font-size: 12px;
}
.form-group {
  margin-bottom: 22px;
  padding: 18px 18px 4px;
  border: 1px solid #eef2f8;
  border-radius: 12px;
  background: #fff;
}
.form-group-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 14px;
  font-size: 15px;
  font-weight: 600;
  color: #0a1e45;
}
.form-group-title .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #2f6bd8;
}
.form-group-title .dot-green {
  background: #1f9e87;
}
.form-group-title .dot-purple {
  background: #7a44f0;
}
.form-group-title .dot-orange {
  background: #ef8520;
}
.feature-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding-bottom: 14px;
}
.feature-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border: 1px solid #e3ebf5;
  border-radius: 999px;
  background: #fbfdff;
  cursor: pointer;
  transition: border-color 0.16s, background 0.16s, color 0.16s;
}
.feature-chip:hover {
  border-color: #bcd4f5;
}
.feature-chip.active {
  border-color: #4f8bff;
  background: #eef5ff;
}
.feature-chip.active .chip-text {
  color: #2f6bd8;
  font-weight: 600;
}
.feature-chip.disabled {
  cursor: not-allowed;
  opacity: 0.7;
}
.feature-chip .chip-check {
  margin-right: 0;
}
.chip-text {
  font-size: 14px;
  color: #46566f;
}
.two-column {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 18px;
}
.one-column {
  display: grid;
  grid-template-columns: 1fr;
}
.material-upload-list {
  display: grid;
  grid-template-columns: 1fr;
  gap: 14px;
  margin-top: 8px;
}
.upload-card {
  border: 1px solid #e8eef7;
  border-radius: 12px;
  padding: 16px 18px;
  background: #fbfdff;
  transition: border-color 0.18s, box-shadow 0.18s;
}
.upload-card:hover {
  border-color: #bcd4f5;
  box-shadow: 0 4px 14px rgba(22, 101, 216, 0.06);
}
.upload-card-head {
  display: flex;
  align-items: center;
  gap: 14px;
}
.upload-card-icon {
  flex: none;
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  font-size: 18px;
  color: #fff;
  background: linear-gradient(135deg, #4f8bff, #2f6bd8);
}
.upload-card-icon.icon-image {
  background: linear-gradient(135deg, #34c0a8, #1f9e87);
}
.upload-card-icon.icon-code {
  background: linear-gradient(135deg, #9b6bff, #7a44f0);
}
.upload-card-icon.icon-file {
  background: linear-gradient(135deg, #f6a23c, #ef8520);
}
.upload-card-meta {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}
.upload-card-title {
  font-size: 15px;
  font-weight: 600;
  color: #0a1e45;
}
.upload-card-hint {
  font-size: 12px;
  color: #8a98ad;
  margin-top: 2px;
}
.upload-empty {
  margin-top: 12px;
  font-size: 13px;
  color: #aab6c8;
}
.upload-file-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
  border-top: 1px dashed #e3ebf5;
  padding-top: 12px;
}
.upload-file-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #edf2f9;
}
.upload-file-ico {
  color: #2f6bd8;
  font-size: 15px;
}
.upload-file-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: #2a3b56;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.upload-file-size {
  font-size: 12px;
  color: #9aa7ba;
  flex: none;
}
.upload-file-del {
  color: #c0cad8;
  font-size: 15px;
  cursor: pointer;
  transition: color 0.15s;
  flex: none;
}
.upload-file-del:hover {
  color: #f5222d;
}
.declaration {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 24px;
  line-height: 1.7;
}
.declaration :deep(.ant-checkbox) { margin-top: 3px; }
.declaration :deep(.ant-checkbox + span) { padding-inline-start: 0; line-height: 1.7; }
.actions {
  display: flex;
  justify-content: space-between;
  margin-top: 32px;
  padding-top: 20px;
  border-top: 1px solid #edf1f7;
}
.rubric-panel {
  height: fit-content;
  padding: 22px;
  border: 1px solid #dcecff;
  border-radius: 12px;
  background: #f7fbff;
}
.rubric-panel h3 {
  margin: 8px 0 16px;
  font-size: 16px;
}
.rubric-item {
  display: flex;
  justify-content: space-between;
  padding: 11px 0;
  border-bottom: 1px solid #e5effa;
  color: #52647f;
}
.rubric-item b {
  color: #165dbe;
}
.hint {
  margin: 16px 0 0;
  color: #71829d;
  font-size: 12px;
  line-height: 1.7;
}
.submission-reminder {
  position: sticky;
  top: 20px;
  padding: 20px;
  border: 1px solid #ffb3b0;
  border-left: 4px solid #f5222d;
  border-radius: 12px;
  background: linear-gradient(180deg, #fff8f7 0%, #fff1f0 100%);
  color: #5c1f24;
  box-shadow: 0 6px 18px rgba(207, 19, 34, 0.08);
}
.submission-reminder-head {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-bottom: 14px;
}
.submission-reminder-head h2 {
  margin: 0;
  color: #cf1322;
  font-size: 17px;
}
.submission-reminder-icon {
  display: inline-flex;
  color: #f5222d;
  font-size: 20px;
}
.submission-reminder-lead,
.submission-reminder-consequence {
  margin: 0;
  font-size: 13px;
  line-height: 1.75;
}
.submission-reminder-lead {
  color: #7a3036;
}
.submission-reminder-list {
  display: grid;
  gap: 9px;
  margin: 15px 0;
  padding: 0 0 0 19px;
  color: #6d2a30;
  font-size: 13px;
  line-height: 1.7;
}
.submission-reminder-list li::marker {
  color: #f5222d;
}
.submission-reminder-consequence {
  padding-top: 14px;
  border-top: 1px solid #ffc9c5;
  color: #a8071a;
  font-weight: 600;
}
@media (max-width: 900px) {
  .content-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 640px) {
  .exam-page {
    padding: 20px 14px;
  }
  .portal-topbar {
    margin: -20px -14px 22px;
    padding: 0 16px;
    gap: 14px;
  }
  .portal-nav {
    gap: 0;
    overflow-x: auto;
  }
  .portal-nav-btn {
    padding: 8px 10px;
    white-space: nowrap;
  }
  .page-header {
    display: block;
  }
  .two-column {
    grid-template-columns: 1fr;
  }
  .submission-steps {
    overflow-x: auto;
  }
  .actions {
    gap: 10px;
  }
}
</style>
