<!--
  v2 门户 - 智能体详情弹窗
  点击卡片时打开，展示该智能体的完整信息 + 复制到我的空间入口
  操作：克隆到当前用户所在租户
-->
<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { AgentDefinitionVO } from '@/types'
import { useAccountStore } from '@/stores'
import { runCloneAgentFlow } from '@/composables/useAgentCloneFlow'
import {
  CalendarOutlined,
  TeamOutlined,
  ToolOutlined,
  DatabaseOutlined,
  ApiOutlined,
  LinkOutlined,
} from '@ant-design/icons-vue'

const props = defineProps<{
  open: boolean
  agent: AgentDefinitionVO | null
}>()

const emit = defineEmits<{
  'update:open': [v: boolean]
  'clone-success': [newAgentId: string]
}>()

const router = useRouter()
const accountStore = useAccountStore()

/** 是否为默认空间（default租户） */
const isDefaultTenant = computed(() => {
  const tenant = accountStore.currentTenant
  return String(tenant?.tenantId ?? '') === '1' || tenant?.tenantCode === 'default'
})

const toolCount = computed(() => props.agent?.tool?.length || 0)
const skillCount = computed(() => props.agent?.hook?.length || 0)
const knowledgeCount = computed(() => props.agent?.knowledgeBase?.length || 0)
const mcpCount = computed(() => props.agent?.mcp?.length || 0)

const useCount = computed(() => {
  const real = props.agent?.used?.length || 0
  if (real > 0) return real
  const id = String(props.agent?.id ?? '')
  if (!id) return 0
  let h = 0
  for (let i = 0; i < id.length; i++) {
    h = (h * 31 + id.charCodeAt(i)) | 0
  }
  return 10 + (Math.abs(h) % 990)
})

const formattedTime = computed(() => {
  if (!props.agent?.updatedAt) return '-'
  const date = new Date(props.agent.updatedAt)
  return date.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
})

function close() {
  emit('update:open', false)
}

async function handleAction() {
  if (!props.agent) return
  
  if (isDefaultTenant.value) {
    // 默认空间用户：立即试用，新标签页打开聊天窗口
    close()
    const routeData = router.resolve({ name: 'Chat', params: { agentId: String(props.agent.id) } })
    window.open(routeData.href, '_blank')
    return true
  }
  
  // 其他租户：复制到我的空间
  const ok = await runCloneAgentFlow({
    sourceAgentId: props.agent.id,
    sourceName: props.agent.name,
    onSuccess: (data) => {
      close()
      emit('clone-success', String(data.id))
      // 跳转到新 agent 的编辑页让用户配模型
      router.push({ name: 'Agent', params: { id: data.id } })
    },
  })
  return ok
}
</script>

<template>
  <AModal
    :open="open"
    :footer="null"
    :width="640"
    :destroy-on-close="true"
    :mask-closable="true"
    class="agent-detail-modal"
    @cancel="close"
  >
    <div v-if="agent" class="agent-detail">
      <!-- 头部：图标 + 名称 + tag -->
      <header class="detail-header">
        <div class="detail-icon">
          <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M20 12H4M12 4v16M8 8l4-4 4 4M8 16l4 4 4-4" />
          </svg>
        </div>
        <div class="detail-title-area">
          <h2 class="detail-name">{{ agent.name }}</h2>
          <div class="detail-tags">
            <span v-if="agent.tag" class="tag tag-blue">{{ agent.tag }}</span>
            <span class="tag tag-gray">{{ agent.agentType === 'CUSTOM' ? '自定义' : 'A2A' }}</span>
            <span class="tag tag-green">
              <TeamOutlined />
              {{ useCount }} 人使用
            </span>
          </div>
        </div>
      </header>

      <!-- 描述 -->
      <section class="detail-section">
        <h3 class="section-title">简介</h3>
        <p class="detail-desc">{{ agent.description || '这个智能体还没有简介' }}</p>
      </section>

      <!-- 能力统计 -->
      <section v-if="toolCount || skillCount || knowledgeCount || mcpCount" class="detail-section">
        <h3 class="section-title">能力配置</h3>
        <div class="detail-stats">
          <div v-if="toolCount" class="stat-item">
            <ToolOutlined class="stat-icon" />
            <span class="stat-label">工具</span>
            <span class="stat-value">{{ toolCount }}</span>
          </div>
          <div v-if="skillCount" class="stat-item">
            <ApiOutlined class="stat-icon" />
            <span class="stat-label">技能</span>
            <span class="stat-value">{{ skillCount }}</span>
          </div>
          <div v-if="knowledgeCount" class="stat-item">
            <DatabaseOutlined class="stat-icon" />
            <span class="stat-label">知识库</span>
            <span class="stat-value">{{ knowledgeCount }}</span>
          </div>
          <div v-if="mcpCount" class="stat-item">
            <LinkOutlined class="stat-icon" />
            <span class="stat-label">MCP</span>
            <span class="stat-value">{{ mcpCount }}</span>
          </div>
        </div>
      </section>

      <!-- 元信息 -->
      <section class="detail-section">
        <h3 class="section-title">元信息</h3>
        <div class="detail-meta">
          <div class="meta-item">
            <CalendarOutlined />
            <span>最近更新：{{ formattedTime }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-key">智能体 ID</span>
            <code>{{ agent.id }}</code>
          </div>
        </div>
      </section>

      <!-- 操作 -->
      <footer class="detail-footer">
        <button class="btn-secondary" type="button" @click="close">关闭</button>
        <button class="btn-primary" type="button" @click="handleAction">
          {{ isDefaultTenant ? '立即试用' : '复制到我的空间' }}
          <svg v-if="!isDefaultTenant" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="14" height="14"><path d="M12 5v14M5 12h14"/></svg>
          <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="14" height="14"><polygon points="5 3 19 12 5 21 5 3"/></svg>
        </button>
      </footer>
    </div>
  </AModal>
</template>

<style scoped lang="scss">
.agent-detail {
  padding: 8px 4px 0;
}

/* 头部 */
.detail-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-bottom: 20px;
  border-bottom: 1px solid #f1f5f9;
  margin-bottom: 20px;
}

.detail-icon {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  display: grid;
  place-items: center;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.25);
}

.detail-title-area {
  flex: 1;
  min-width: 0;
}

.detail-name {
  margin: 0 0 8px;
  font-size: 20px;
  font-weight: 700;
  color: #1e293b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.detail-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 500;
}

.tag-blue {
  background: #eff6ff;
  color: #2563eb;
}

.tag-gray {
  background: #f1f5f9;
  color: #475569;
}

.tag-green {
  background: #f0fdf4;
  color: #16a34a;
}

/* 段落 */
.detail-section {
  margin-bottom: 20px;

  &:last-of-type {
    margin-bottom: 0;
  }
}

.section-title {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 600;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.detail-desc {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: #475569;
}

/* 能力网格 */
.detail-stats {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 10px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.stat-icon {
  font-size: 16px;
  color: #2563eb;
}

.stat-label {
  font-size: 13px;
  color: #64748b;
  flex: 1;
}

.stat-value {
  font-size: 14px;
  font-weight: 700;
  color: #1e293b;
}

/* 元信息 */
.detail-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #475569;
}

.meta-key {
  color: #94a3b8;
  min-width: 70px;
}

code {
  font-family: 'JetBrains Mono', Menlo, Consolas, monospace;
  font-size: 12px;
  background: #f1f5f9;
  padding: 2px 6px;
  border-radius: 4px;
  color: #475569;
}

/* 操作按钮 */
.detail-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid #f1f5f9;
}

.btn-secondary,
.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 18px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid transparent;
}

.btn-secondary {
  background: #f1f5f9;
  color: #475569;
  border-color: #e2e8f0;

  &:hover {
    background: #e2e8f0;
    color: #1e293b;
  }
}

.btn-primary {
  background: #2563eb;
  color: #fff;

  &:hover {
    background: #1d4ed8;
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(37, 99, 235, 0.3);
  }
}
</style>
