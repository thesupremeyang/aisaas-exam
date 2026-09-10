<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  CloseOutlined,
  FileTextOutlined,
  LoadingOutlined,
  PaperClipOutlined,
  ReloadOutlined,
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { listCandidateQuestions, type ExamQuestion } from '@/api/exam'
import { download as downloadAttach } from '@/api/attach'
import { useAccountStore } from '@/stores'

const accountStore = useAccountStore()

const triggerRef = ref<HTMLButtonElement>()
const panelRef = ref<HTMLElement>()
const expanded = ref(false)
const loading = ref(false)
const loaded = ref(false)
const loadFailed = ref(false)
const questions = ref<ExamQuestion[]>([])
const activeQuestionIndex = ref(0)
const campaignId = ref('')
const candidateMode = ref(false)
const candidateAccount = ref('')
let requestVersion = 0

const isExamCandidate = computed(() => {
  const currentAccount = accountStore.userInfo?.id || accountStore.userInfo?.username || ''
  return (
    candidateMode.value &&
    !!campaignId.value &&
    !!currentAccount &&
    candidateAccount.value === String(currentAccount) &&
    accountStore.userInfo?.username?.toLowerCase() !== 'admin'
  )
})

const activeQuestion = computed(() => questions.value[activeQuestionIndex.value])
const questionCountLabel = computed(() =>
  questions.value.length ? `${questions.value.length} 题` : '试题',
)

function syncExamContext() {
  campaignId.value = sessionStorage.getItem('exam:campaignId') || ''
  candidateMode.value = sessionStorage.getItem('exam:candidate-mode') === '1'
  candidateAccount.value = sessionStorage.getItem('exam:candidate-account') || ''
}

function resetQuestions() {
  requestVersion += 1
  expanded.value = false
  loading.value = false
  loaded.value = false
  loadFailed.value = false
  questions.value = []
  activeQuestionIndex.value = 0
}

async function loadQuestions() {
  if (!isExamCandidate.value || !campaignId.value) return

  const currentRequest = ++requestVersion
  loading.value = true
  loadFailed.value = false
  try {
    const response = await listCandidateQuestions(campaignId.value)
    if (currentRequest !== requestVersion) return
    questions.value = response.data.data || []
    activeQuestionIndex.value = Math.min(
      activeQuestionIndex.value,
      Math.max(questions.value.length - 1, 0),
    )
    loaded.value = true
  } catch {
    if (currentRequest !== requestVersion) return
    questions.value = []
    activeQuestionIndex.value = 0
    loadFailed.value = true
  } finally {
    if (currentRequest === requestVersion) loading.value = false
  }
}

function togglePanel() {
  expanded.value = !expanded.value
  if (expanded.value && !loaded.value && !loading.value) void loadQuestions()
}

function closePanel() {
  expanded.value = false
}

function selectQuestion(index: number) {
  activeQuestionIndex.value = index
}

function questionTitle(question: ExamQuestion | undefined, index: number): string {
  return question?.title?.trim() || `第 ${index + 1} 题`
}

function formatFileSize(bytes?: number): string {
  if (!bytes) return ''
  const mb = bytes / 1024 / 1024
  return mb >= 1 ? `${mb.toFixed(1)} MB` : `${Math.round(bytes / 1024)} KB`
}

async function downloadAttachment(question: ExamQuestion) {
  if (!question.attachmentUrl) return
  try {
    const response = await downloadAttach(question.attachmentUrl)
    const blob = new Blob([response.data])
    const url = window.URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = question.attachmentName || 'attachment'
    anchor.click()
    window.URL.revokeObjectURL(url)
  } catch {
    message.error('附件下载失败，请稍后重试')
  }
}

function onDocumentPointerDown(event: PointerEvent) {
  if (!expanded.value) return
  const target = event.target as Node
  if (panelRef.value?.contains(target) || triggerRef.value?.contains(target)) return
  closePanel()
}

function onDocumentKeydown(event: KeyboardEvent) {
  if (event.key !== 'Escape' || !expanded.value) return
  event.preventDefault()
  closePanel()
  void nextTick(() => triggerRef.value?.focus())
}

watch(
  () => [isExamCandidate.value, campaignId.value] as const,
  ([enabled]) => {
    if (!enabled) {
      resetQuestions()
      return
    }
    void loadQuestions()
  },
  { immediate: true },
)

onMounted(() => {
  syncExamContext()
  window.addEventListener('exam:context-updated', syncExamContext)
  document.addEventListener('pointerdown', onDocumentPointerDown)
  document.addEventListener('keydown', onDocumentKeydown)
})

onBeforeUnmount(() => {
  requestVersion += 1
  window.removeEventListener('exam:context-updated', syncExamContext)
  document.removeEventListener('pointerdown', onDocumentPointerDown)
  document.removeEventListener('keydown', onDocumentKeydown)
})
</script>

<template>
  <Teleport to="body">
    <aside v-if="isExamCandidate" class="exam-question-float">
      <button
        ref="triggerRef"
        class="exam-question-trigger"
        :class="{ open: expanded }"
        type="button"
        :aria-expanded="expanded"
        aria-controls="exam-question-float-panel"
        @click="togglePanel"
      >
        <FileTextOutlined aria-hidden="true" />
        <span>查看试题</span>
        <small>{{ questionCountLabel }}</small>
      </button>

      <section
        id="exam-question-float-panel"
        ref="panelRef"
        class="exam-question-panel"
        :class="{ open: expanded }"
        role="dialog"
        aria-modal="false"
        aria-labelledby="exam-question-float-title"
        :aria-hidden="!expanded"
      >
        <header class="exam-question-panel-header">
          <div>
            <span class="exam-question-panel-eyebrow">EXAM BRIEF</span>
            <h2 id="exam-question-float-title">本次考试试题</h2>
          </div>
          <button
            class="exam-question-icon-button"
            type="button"
            aria-label="关闭试题查看窗"
            @click="closePanel"
          >
            <CloseOutlined />
          </button>
        </header>

        <div v-if="loading" class="exam-question-state" aria-live="polite">
          <LoadingOutlined spin />
          <span>正在加载试题…</span>
        </div>

        <div v-else-if="loadFailed" class="exam-question-state error" role="alert">
          <span>试题加载失败</span>
          <button type="button" @click="loadQuestions"><ReloadOutlined />重新加载</button>
        </div>

        <div v-else-if="!questions.length" class="exam-question-state">
          <span>当前考试暂无试题</span>
        </div>

        <template v-else-if="activeQuestion">
          <nav v-if="questions.length > 1" class="exam-question-tabs" aria-label="试题列表">
            <button
              v-for="(question, index) in questions"
              :key="question.id || index"
              type="button"
              :class="{ active: activeQuestionIndex === index }"
              :aria-pressed="activeQuestionIndex === index"
              @click="selectQuestion(index)"
            >
              {{ `第 ${index + 1} 题` }}
            </button>
          </nav>

          <div class="exam-question-content">
            <div class="exam-question-title-row">
              <span class="exam-question-number">{{
                `0${activeQuestionIndex + 1}`.slice(-2)
              }}</span>
              <div>
                <h3>{{ questionTitle(activeQuestion, activeQuestionIndex) }}</h3>
                <p v-if="activeQuestion.score">
                  <span>{{ activeQuestion.score }} 分</span>
                </p>
              </div>
            </div>

            <section v-if="activeQuestion.taskScenario" class="exam-question-block">
              <h4>任务场景</h4>
              <p>{{ activeQuestion.taskScenario }}</p>
            </section>

            <section v-if="activeQuestion.taskRequirement" class="exam-question-block">
              <h4>任务要求</h4>
              <p>{{ activeQuestion.taskRequirement }}</p>
            </section>

            <section
              v-if="activeQuestion.submissionDeliverable"
              class="exam-question-block deliverable"
            >
              <h4>提交成果</h4>
              <p>{{ activeQuestion.submissionDeliverable }}</p>
            </section>

            <button
              v-if="activeQuestion.attachmentUrl"
              class="exam-question-attachment"
              type="button"
              @click="downloadAttachment(activeQuestion)"
            >
              <PaperClipOutlined aria-hidden="true" />
              <span>
                <strong>{{ activeQuestion.attachmentName || '试题附件' }}</strong>
                <small v-if="activeQuestion.attachmentSize">{{
                  formatFileSize(activeQuestion.attachmentSize)
                }}</small>
              </span>
              <em>下载</em>
            </button>
          </div>
        </template>
      </section>
    </aside>
  </Teleport>
</template>

<style scoped lang="scss">
.exam-question-float {
  position: fixed;
  z-index: 1101;
  top: 80px;
  right: 24px;
}

.exam-question-trigger,
.exam-question-icon-button,
.exam-question-tabs button,
.exam-question-state button,
.exam-question-attachment {
  font: inherit;
}

.exam-question-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 42px;
  padding: 0 10px 0 13px;
  border: 1px solid rgba(37, 99, 235, 0.22);
  border-radius: 12px;
  color: #1d4ed8;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 8px 22px rgba(30, 64, 175, 0.14);
  cursor: pointer;
  font-size: 13px;
  font-weight: 650;
  letter-spacing: 0.01em;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  transition:
    color 160ms ease,
    border-color 160ms ease,
    background-color 160ms ease,
    box-shadow 160ms ease,
    transform 160ms cubic-bezier(0.23, 1, 0.32, 1);

  > small {
    min-width: 28px;
    padding: 2px 6px;
    border-radius: 6px;
    color: #fff;
    background: #2563eb;
    font-size: 11px;
    font-weight: 700;
    line-height: 1.35;
  }

  &.open {
    border-color: #2563eb;
    color: #fff;
    background: #2563eb;
    box-shadow: 0 12px 28px rgba(37, 99, 235, 0.28);

    > small {
      color: #1d4ed8;
      background: #fff;
    }
  }

  &:active {
    transform: scale(0.97);
  }
}

.exam-question-trigger:focus-visible,
.exam-question-icon-button:focus-visible,
.exam-question-tabs button:focus-visible,
.exam-question-state button:focus-visible,
.exam-question-attachment:focus-visible {
  outline: 3px solid rgba(37, 99, 235, 0.32);
  outline-offset: 3px;
}

.exam-question-panel {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  display: flex;
  flex-direction: column;
  width: min(430px, calc(100vw - 32px));
  max-height: min(700px, calc(100vh - 108px));
  overflow: hidden;
  border: 1px solid rgba(191, 219, 254, 0.9);
  border-radius: 18px;
  color: #172554;
  background: rgba(255, 255, 255, 0.98);
  box-shadow:
    0 22px 56px rgba(30, 64, 175, 0.2),
    0 2px 8px rgba(30, 64, 175, 0.06);
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  transform: translateY(-8px) scale(0.98);
  transform-origin: top right;
  transition:
    transform 200ms cubic-bezier(0.23, 1, 0.32, 1),
    opacity 160ms ease,
    visibility 0s linear 200ms;

  &.open {
    visibility: visible;
    pointer-events: auto;
    opacity: 1;
    transform: translateY(0) scale(1);
    transition-delay: 0s;
  }
}

.exam-question-panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 18px 15px;
  border-bottom: 1px solid #e5efff;
  background: linear-gradient(135deg, #f8fbff 0%, #eef5ff 100%);

  h2 {
    margin: 3px 0 0;
    color: #172554;
    font-size: 17px;
    line-height: 1.35;
  }
}

.exam-question-panel-eyebrow {
  color: #3b82f6;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.14em;
}

.exam-question-icon-button {
  display: inline-grid;
  flex: 0 0 auto;
  width: 30px;
  height: 30px;
  place-items: center;
  margin-top: -2px;
  padding: 0;
  border: 0;
  border-radius: 8px;
  color: #64748b;
  background: transparent;
  cursor: pointer;
  transition:
    color 140ms ease,
    background-color 140ms ease,
    transform 140ms cubic-bezier(0.23, 1, 0.32, 1);

  &:hover {
    color: #1d4ed8;
    background: rgba(37, 99, 235, 0.09);
  }

  &:active {
    transform: scale(0.95);
  }
}

.exam-question-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  min-height: 164px;
  padding: 28px;
  color: #64748b;
  font-size: 13px;

  &.error {
    flex-direction: column;
    color: #b45309;
  }

  button {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    padding: 5px 8px;
    border: 0;
    border-radius: 7px;
    color: #1d4ed8;
    background: #eff6ff;
    cursor: pointer;
    transition:
      background-color 140ms ease,
      transform 140ms cubic-bezier(0.23, 1, 0.32, 1);

    &:hover {
      background: #dbeafe;
    }

    &:active {
      transform: scale(0.97);
    }
  }
}

.exam-question-tabs {
  display: flex;
  flex: 0 0 auto;
  gap: 7px;
  padding: 12px 18px 0;
  overflow-x: auto;
  scrollbar-width: thin;

  button {
    flex: 0 0 auto;
    padding: 6px 10px;
    border: 1px solid #dbeafe;
    border-radius: 8px;
    color: #64748b;
    background: #fff;
    cursor: pointer;
    font-size: 12px;
    transition:
      color 140ms ease,
      border-color 140ms ease,
      background-color 140ms ease,
      transform 140ms cubic-bezier(0.23, 1, 0.32, 1);

    &:hover,
    &.active {
      border-color: #93c5fd;
      color: #1d4ed8;
      background: #eff6ff;
    }

    &.active {
      font-weight: 700;
    }

    &:active {
      transform: scale(0.97);
    }
  }
}

.exam-question-content {
  min-height: 0;
  overflow-y: auto;
  padding: 18px;
  scrollbar-color: #bfdbfe transparent;
}

.exam-question-title-row {
  display: flex;
  gap: 12px;
  margin-bottom: 18px;

  h3 {
    margin: 1px 0 5px;
    color: #172554;
    font-size: 16px;
    line-height: 1.5;
  }

  p {
    display: flex;
    align-items: center;
    gap: 7px;
    margin: 0;
    color: #64748b;
    font-size: 12px;

    i {
      width: 3px;
      height: 3px;
      border-radius: 50%;
      background: #94a3b8;
    }
  }
}

.exam-question-number {
  display: grid;
  flex: 0 0 auto;
  width: 32px;
  height: 32px;
  place-items: center;
  border-radius: 9px;
  color: #1d4ed8;
  background: #dbeafe;
  font-size: 12px;
  font-weight: 800;
}

.exam-question-block {
  margin-top: 13px;
  padding: 13px 14px;
  border: 1px solid #e5efff;
  border-radius: 11px;
  background: #fbfdff;

  h4 {
    margin: 0 0 6px;
    color: #1e3a8a;
    font-size: 12px;
    font-weight: 750;
  }

  p {
    margin: 0;
    color: #475569;
    font-size: 13px;
    line-height: 1.75;
    white-space: pre-wrap;
    word-break: break-word;
  }

  &.deliverable {
    border-color: #c7d2fe;
    background: #f8faff;
  }
}

.exam-question-attachment {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 10px;
  margin-top: 13px;
  padding: 11px 12px;
  border: 1px solid #dbeafe;
  border-radius: 11px;
  color: #2563eb;
  background: #fff;
  cursor: pointer;
  text-align: left;
  transition:
    border-color 140ms ease,
    background-color 140ms ease,
    transform 140ms cubic-bezier(0.23, 1, 0.32, 1);

  > span {
    display: flex;
    min-width: 0;
    flex: 1;
    flex-direction: column;
    gap: 2px;
  }

  strong {
    overflow: hidden;
    color: #334155;
    font-size: 12px;
    font-weight: 650;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  small {
    color: #94a3b8;
    font-size: 11px;
  }

  em {
    font-size: 12px;
    font-style: normal;
    font-weight: 650;
  }

  &:hover {
    border-color: #93c5fd;
    background: #eff6ff;
  }

  &:active {
    transform: scale(0.98);
  }
}

@media (hover: hover) and (pointer: fine) {
  .exam-question-trigger:hover {
    border-color: #60a5fa;
    box-shadow: 0 11px 26px rgba(30, 64, 175, 0.18);
    transform: translateY(-1px);
  }

  .exam-question-trigger.open:hover {
    background: #1d4ed8;
  }
}

@media (max-width: 768px) {
  .exam-question-float {
    top: 72px;
    right: 12px;
  }

  .exam-question-trigger {
    gap: 6px;
    min-height: 38px;
    padding: 0 8px 0 11px;
    border-radius: 10px;

    > span {
      font-size: 12px;
    }
  }

  .exam-question-panel {
    width: min(430px, calc(100vw - 24px));
    max-height: calc(100vh - 94px);
    border-radius: 15px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .exam-question-trigger,
  .exam-question-panel,
  .exam-question-icon-button,
  .exam-question-tabs button,
  .exam-question-state button,
  .exam-question-attachment {
    transition-property: opacity, color, border-color, background-color, box-shadow;
    transition-duration: 120ms;
  }

  .exam-question-panel {
    transform: none;
  }
}
</style>
