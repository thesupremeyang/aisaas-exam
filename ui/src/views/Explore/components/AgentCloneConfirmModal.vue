<!--
  广场克隆 agent 确认弹窗（方案 G）

  设计：
  - 父组件（AgentCloneButton 或独立测试页）通过 ref 调用 open(sourceAgentId, name)
  - 用户点"确定"后 emit('confirm')，父组件负责调用 API 并跳转
  - 父组件负责把 loading 状态通过 prop 传进来控制按钮

  @author agent-clone-feature
-->
<template>
  <AModal
    v-model:open="visible"
    title="添加到我的工作台"
    :confirm-loading="loading"
    :mask-closable="false"
    ok-text="确认克隆"
    cancel-text="取消"
    @ok="handleOk"
    @cancel="handleCancel"
  >
    <div class="agent-clone-confirm">
      <p class="intro">
        将把
        <b class="agent-name">{{ agentName || '该智能体' }}</b>
        复制到您所在租户下作为本地副本。
      </p>

      <div class="notice-title">注意事项</div>
      <ul class="notice-list">
        <li>关联的模型、知识库、提示词模板等资源<strong>不会复制</strong>，需您重新配置</li>
        <li>克隆后默认<strong>禁用</strong>，进入编辑页修改并启用后即可使用</li>
        <li>本副本与原智能体相互独立，后续修改互不影响</li>
        <li>可在"智能体管理"列表中找到此副本</li>
      </ul>
    </div>
  </AModal>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps<{
  loading?: boolean
}>()

const emit = defineEmits<{
  (e: 'confirm', payload: { sourceAgentId: string | number; name: string }): void
  (e: 'cancel'): void
}>()

const visible = ref(false)
const agentName = ref('')
const sourceAgentId = ref<string | number | null>(null)

/** 父组件调用：唤起弹窗 */
function open(id: string | number, name: string) {
  sourceAgentId.value = id
  agentName.value = name
  visible.value = true
}

function close() {
  visible.value = false
}

function handleOk() {
  if (sourceAgentId.value == null) {
    return
  }
  emit('confirm', {
    sourceAgentId: sourceAgentId.value,
    name: agentName.value,
  })
}

function handleCancel() {
  emit('cancel')
  close()
}

defineExpose({ open, close })
</script>

<style scoped lang="scss">
.agent-clone-confirm {
  padding: 4px 0;
  font-size: 14px;
  line-height: 1.6;

  .intro {
    margin: 0 0 16px 0;

    .agent-name {
      color: #1677ff;
    }
  }

  .notice-title {
    font-weight: 600;
    margin-bottom: 8px;
  }

  .notice-list {
    margin: 0;
    padding-left: 20px;
    color: #595959;

    li {
      margin-bottom: 6px;
    }

    strong {
      color: #d4380d;
    }
  }
}
</style>
