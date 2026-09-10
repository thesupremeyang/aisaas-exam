<!--
  广场克隆 agent 按钮（方案 G）

  设计：
  - 接收 sourceAgentId + name 作为 props
  - 点击时唤起 AgentCloneConfirmModal
  - 父组件也可以通过 ref 直接调用 openModal()

  @author agent-clone-feature
-->
<template>
  <AButton
    type="primary"
    :loading="loading"
    @click="handleClick"
  >
    <template #icon>
      <PlusOutlined />
    </template>
    添加到我的工作台
  </AButton>

  <AgentCloneConfirmModal
    ref="modalRef"
    :loading="loading"
    @confirm="handleConfirm"
    @cancel="handleCancel"
  />
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import AgentCloneConfirmModal from './AgentCloneConfirmModal.vue'
import { cloneAgentByPortal } from '@/api/v2PortalClone'

const props = defineProps<{
  /** 源 agent 主键 */
  sourceAgentId: string | number
  /** 源 agent 名称（仅显示用） */
  name?: string
}>()

const emit = defineEmits<{
  (e: 'success', payload: { id: string; name: string }): void
  (e: 'error', error: Error): void
}>()

const modalRef = ref<InstanceType<typeof AgentCloneConfirmModal> | null>(null)
const loading = ref(false)

function handleClick() {
  modalRef.value?.open(props.sourceAgentId, props.name ?? '')
}

async function handleConfirm(payload: { sourceAgentId: string | number; name: string }) {
  loading.value = true
  try {
    const res = await cloneAgentByPortal(payload.sourceAgentId)
    const data = res?.data?.data
    if (!data?.id) {
      throw new Error('克隆响应数据为空')
    }
    message.success(`已克隆到我的工作台：${data.name}`)
    modalRef.value?.close()
    emit('success', { id: data.id, name: data.name })
  } catch (e: any) {
    const errMsg = e?.response?.data?.msg || e?.message || '克隆失败'
    message.error(errMsg)
    emit('error', e instanceof Error ? e : new Error(errMsg))
  } finally {
    loading.value = false
  }
}

function handleCancel() {
  // 用户取消，无需额外处理
}

/** 对外暴露：让父组件能程序化打开 */
function openModal() {
  handleClick()
}

defineExpose({ openModal })
</script>
