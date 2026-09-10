<script setup lang="ts">
import { useAccountStore } from '@/stores'
import { QuestionCircleOutlined, UndoOutlined } from '@ant-design/icons-vue'

const { setRefresh } = useAccountStore()

const helpUrl = import.meta.env.DEV
  ? `${import.meta.env.BASE_URL}doc.html#/`
  : '/doc.html#/'

function openHelp() {
  const helpWindow = window.open(helpUrl, '_blank', 'noopener,noreferrer')
  if (!helpWindow) {
    window.location.assign(helpUrl)
  }
}
</script>

<template>
  <div class="toolbar-v2">
    <ATooltip placement="right">
      <template #title><span>帮助</span></template>
      <AButton class="toolbar-v2-btn" shape="circle" type="text" @click="openHelp">
        <template #icon><QuestionCircleOutlined /></template>
      </AButton>
    </ATooltip>

    <ATooltip placement="right">
      <template #title><span>刷新</span></template>
      <AButton class="toolbar-v2-btn" shape="circle" type="text" @click="setRefresh">
        <template #icon><UndoOutlined /></template>
      </AButton>
    </ATooltip>
  </div>
</template>

<style scoped lang="scss">
.toolbar-v2 {
  padding: 10px 14px;
  display: flex;
  align-items: center;
  gap: 4px;
  border-top: 1px solid #e2e8f0;

  .toolbar-v2-btn {
    border: none;
    color: #64748b;
    width: 30px;
    height: 30px;
    font-size: 13px;
    border-radius: 6px;

    &:hover {
      color: #2563eb;
      background: #f1f5f9;
    }
  }
}
</style>
