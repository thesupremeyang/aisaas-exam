<!--
  克隆功能独立测试页（方案 G）

  用途：
  - 验证 "POST /api/v2-portal/clone/agent" 端到端流程
  - 输入 sourceAgentId 和名称 → 点击"克隆" → 后端把 agent 克隆到当前用户租户
  - 不修改 Explore/index.vue，不依赖 AgentCard / AgentDetailModal

  访问路径：#/agent-clone-demo（由 agentCloneHandler.installCloneRoutes 注入）
  也可带 query：?sourceAgentId=xxx&name=yyy 自动填充

  @author agent-clone-feature
-->
<template>
  <div class="agent-clone-test-page">
    <ACard title="广场克隆测试（方案 G）" :bordered="false" class="header-card">
      <p class="tip">
        本页面用于验证"把智能体从广场克隆到当前用户租户"的端到端流程。<br>
        不会修改任何现有文件，仅依赖新增文件。
      </p>
    </ACard>

    <ACard title="选择源 agent" class="form-card">
      <AForm :label-col="{ span: 4 }" :wrapper-col="{ span: 16 }">
        <AFormItem label="源 agentId">
          <AInput
            v-model:value="sourceAgentId"
            placeholder="请输入源 agent 主键（从广场接口获取）"
            allow-clear
          />
        </AFormItem>
        <AFormItem label="源 agent 名称">
          <AInput
            v-model:value="sourceName"
            placeholder="仅用于显示，可不填"
            allow-clear
          />
        </AFormItem>
        <AFormItem label="从广场选">
          <ASelect
            v-model:value="selectedFromList"
            placeholder="或从下方广场列表中选择"
            allow-clear
            style="width: 100%"
            :options="listOptions"
            @change="handlePick"
          />
        </AFormItem>
        <AFormItem :wrapper-col="{ offset: 4, span: 16 }">
          <AgentCloneButton
            :source-agent-id="sourceAgentId"
            :name="sourceName"
            @success="handleSuccess"
          />
          <AButton style="margin-left: 12px" @click="loadList">刷新广场列表</AButton>
        </AFormItem>
      </AForm>
    </ACard>

    <ACard title="广场列表（用于挑源 agent）" class="list-card">
      <div v-if="loading" class="loading">加载中…</div>
      <div v-else-if="agents.length === 0" class="empty">暂无数据</div>
      <ARow v-else :gutter="[16, 16]">
        <ACol v-for="a in agents" :key="a.id" :xs="24" :sm="12" :md="8" :lg="6">
          <ACard size="small" :title="a.name" hoverable>
            <p class="meta">agentCode: {{ a.agentCode }}</p>
            <p class="meta">tenantId: {{ a.tenantId }}</p>
            <p class="meta">enabled: {{ a.enabled }}</p>
            <AButton type="link" @click="handlePick(a.id, a.name)">选这个</AButton>
          </ACard>
        </ACol>
      </ARow>
    </ACard>

    <ACard v-if="result" title="克隆结果" class="result-card">
      <pre>{{ JSON.stringify(result, null, 2) }}</pre>
    </ACard>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import AgentCloneButton from './AgentCloneButton.vue'
import * as v2PortalApi from '@/api/v2Portal'
import type { AgentDefinitionVO } from '@/types'
import { openCloneModal } from '@/utils/agentCloneHandler'

const route = useRoute()
const router = useRouter()

const sourceAgentId = ref<string>('')
const sourceName = ref<string>('')
const selectedFromList = ref<string | undefined>(undefined)
const listOptions = ref<{ value: string; label: string }[]>([])

const agents = ref<any[]>([])
const loading = ref(false)
const result = ref<any>(null)

onMounted(async () => {
  // 暴露全局方法，供非测试页调用
  ;(window as any).__openAgentCloneModal = (opts: { sourceAgentId: string | number; name?: string }) => {
    sourceAgentId.value = String(opts.sourceAgentId)
    sourceName.value = opts.name ?? ''
  }

  // 从 query 恢复
  const qid = route.query.sourceAgentId
  const qname = route.query.name
  if (qid) {
    sourceAgentId.value = String(qid)
  }
  if (qname) {
    sourceName.value = String(qname)
  }

  await loadList()
})

async function loadList() {
  loading.value = true
  try {
    const res = await v2PortalApi.pageByPortal({
      current: 1,
      size: 12,
    } as any)
    const rows: AgentDefinitionVO[] = (res?.data?.data as any)?.records ?? []
    agents.value = rows
    listOptions.value = rows.map((a: any) => ({
      value: String(a.id),
      label: `${a.name} (${a.agentCode})`,
    }))
  } catch (e: any) {
    message.warning('加载广场列表失败：' + (e?.message ?? ''))
  } finally {
    loading.value = false
  }
}

function handlePick(id: string | number, name?: string) {
  sourceAgentId.value = String(id)
  if (name) {
    sourceName.value = name
  }
}

function handleSuccess(payload: { id: string; name: string }) {
  result.value = { ok: true, ...payload, at: new Date().toISOString() }
  // 跳到新 agent 的编辑页（让用户配置模型等）
  router.push({ name: 'Agent', params: { id: payload.id } })
}
</script>

<style scoped lang="scss">
.agent-clone-test-page {
  max-width: 1100px;
  margin: 24px auto;
  padding: 0 16px;

  .header-card { margin-bottom: 16px; .tip { color: #595959; margin: 0; } }
  .form-card { margin-bottom: 16px; }
  .list-card { margin-bottom: 16px; .meta { margin: 0; color: #8c8c8c; font-size: 12px; } .loading, .empty { padding: 24px; text-align: center; color: #8c8c8c; } }
  .result-card pre { background: #fafafa; padding: 12px; border-radius: 4px; margin: 0; }
}
</style>
