<script setup lang="ts">
import ChatInput from './ChatInput.vue'
import { computed } from "vue";

const props = defineProps<{
  messageSize: number
  headline: string
  inputValue: string
  agentId: string
  description?: string
  starterQuestions?: string[]
  uploadedFiles?: import('@/types').UploadedFileItem[]
  isRunning?: boolean
  memoryActive?: boolean
  planActive?: boolean
  enableMemory?: boolean
  enablePlanning?: boolean
  toolProcessActive?: boolean
  showToolProcess?: boolean
  allowUploadFileType?: string[]
  sessionId?: string | null
  mentionAllowed?: boolean
  hasCodeExecutionConfig?: boolean
  compact?: boolean
}>()

const needInit = computed(() => {
  return props.hasCodeExecutionConfig && props.messageSize === 0
})

const useStarterQuestion = (question: string) => {
  emit('update:inputValue', question)
}

const emit = defineEmits<{
  (e: 'update:inputValue', value: string): void
  (e: 'update:uploadedFiles', value: import('@/types').UploadedFileItem[]): void
  (e: 'send'): void
  (e: 'memory', value: boolean): void
  (e: 'plan', value: boolean): void
  (e: 'toolProcess', value: boolean): void
  (e: 'newSession'): void
}>()
</script>

<template>
  <div :class="['chat-welcome', { 'chat-welcome-compact': compact }]">
    <h2 class="chat-welcome-title" :title="headline">{{ headline }}</h2>
    <p v-if="description" class="chat-welcome-desc" :title="description">{{ description }}</p>
    <div v-if="starterQuestions?.length" class="chat-starter-questions">
      <button
        v-for="question in starterQuestions"
        :key="question"
        type="button"
        class="chat-starter-question"
        @click="useStarterQuestion(question)"
      >
        {{ question }}
      </button>
    </div>
    <div class="chat-input-outer chat-welcome-input">
      <ChatInput
        :model-value="inputValue"
        :agent-id="agentId"
        :uploaded-files="uploadedFiles"
        :isRunning="isRunning"
        :memory-active="memoryActive"
        :plan-active="planActive"
        :enable-memory="enableMemory"
        :enable-planning="enablePlanning"
        :allow-upload-file-type="allowUploadFileType"
        :show-tool-process="showToolProcess"
        :tool-process-active="toolProcessActive"
        :session-id="sessionId"
        :mention-allowed="mentionAllowed"
        :need-init="needInit"
        @update:model-value="$emit('update:inputValue', $event)"
        @update:uploaded-files="$emit('update:uploadedFiles', $event)"
        @memory="$emit('memory', $event)"
        @plan="$emit('plan', $event)"
        @toolProcess="$emit('toolProcess', $event)"
        @send="$emit('send')"
        @new-session="$emit('newSession')"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/chat/index.scss' as *;
</style>

<style scoped>
.chat-starter-questions {
  display: grid;
  gap: 10px;
  width: min(760px, 100%);
  margin: 18px auto 4px;
}

.chat-starter-question {
  padding: 12px 16px;
  color: var(--text-color, #1f2937);
  text-align: left;
  background: var(--component-background, #fff);
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 12px;
  cursor: pointer;
  transition: border-color 0.2s, transform 0.2s;
}

.chat-starter-question:hover {
  border-color: #1677ff;
  transform: translateY(-1px);
}

.chat-welcome-compact {
  box-sizing: border-box;
  width: 100%;
  min-height: 100%;
  justify-content: flex-start;
  padding: 54px 16px 14px;
}

.chat-welcome-compact .chat-welcome-title {
  margin-bottom: 10px;
  font-size: 28px;
  line-height: 1.28;
}

.chat-welcome-compact .chat-welcome-desc {
  max-width: 350px;
  margin-bottom: 0;
  font-size: 14px;
  line-height: 1.6;
}

.chat-welcome-compact .chat-starter-questions {
  width: 100%;
  max-width: none;
  margin: 48px auto 0;
  gap: 10px;
}

.chat-welcome-compact .chat-starter-question {
  min-height: 50px;
  padding: 13px 14px;
  border-radius: 11px;
  font-size: 13px;
  line-height: 1.45;
}

.chat-welcome-compact .chat-welcome-input {
  width: 100%;
  max-width: none;
  margin-top: auto;
  padding: 0;
}

@media (max-height: 720px) {
  .chat-welcome-compact {
    padding-top: 40px;
  }

  .chat-welcome-compact .chat-starter-questions {
    margin-top: 36px;
  }
}
</style>
