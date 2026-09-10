<!--
  v2 门户 - 智能体卡片
  来源参考：apboa-master-Back 的 ExploreAgentCard.vue，按本系统 AgentDefinitionVO 字段做了适配
  - avatar / name / description / agentType / tag / updatedAt 直接读取
  - toolCount   = tool?.length
  - skillCount  = hook?.length
  - knowledgeCount = knowledgeBase?.length
  - mcpCount    = mcp?.length
  - trialCount  本系统暂无统计字段，传 0 或由父级传
-->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAccountStore } from '@/stores/modules/account'
import type { AgentDefinitionVO } from '@/types'

const props = defineProps<{ data: AgentDefinitionVO; trialCount?: number }>()

const emit = defineEmits<{
  view: [agent: AgentDefinitionVO];
  try: [agent: AgentDefinitionVO];
  clone: [agent: AgentDefinitionVO];
}>()

const router = useRouter()
const accountStore = useAccountStore()

/** 是否为默认空间（default租户） */
const isDefaultTenant = computed(() => {
  const tenant = accountStore.currentTenant
  return String(tenant?.tenantId ?? '') === '1' || tenant?.tenantCode === 'default'
})

const avatarError = ref(false)

/* ===================== 分类图标（参考 Back 的实现） ===================== */
/**
 * 分类样式映射：按 tag 字段匹配 iconBg / iconSvg
 * 未匹配时使用 iconBg(name) 计算稳定渐变
 */
interface CategoryStyle {
  iconBg: string
  iconSvg: string
  tagBg: string
  tagColor: string
}

const categoryStyleMap: Record<string, CategoryStyle> = {
  AI应用:     { iconBg: 'linear-gradient(135deg, #3b82f6, #6366f1)', iconSvg: 'cube',    tagBg: '#eff6ff', tagColor: '#3b82f6' },
  文案写作:   { iconBg: 'linear-gradient(135deg, #8b5cf6, #7c3aed)', iconSvg: 'sparkle', tagBg: '#f5f3ff', tagColor: '#7c3aed' },
  内容生成:   { iconBg: 'linear-gradient(135deg, #a855f7, #9333ea)', iconSvg: 'sparkle', tagBg: '#faf5ff', tagColor: '#9333ea' },
  AI教育:     { iconBg: 'linear-gradient(135deg, #10b981, #059669)', iconSvg: 'book',    tagBg: '#ecfdf5', tagColor: '#059669' },
  内容运营:   { iconBg: 'linear-gradient(135deg, #06b6d4, #0891b2)', iconSvg: 'chart',   tagBg: '#ecfeff', tagColor: '#0891b2' },
  办公:       { iconBg: 'linear-gradient(135deg, #f59e0b, #d97706)', iconSvg: 'window',  tagBg: '#fffbeb', tagColor: '#d97706' },
  办公提效:   { iconBg: 'linear-gradient(135deg, #f59e0b, #d97706)', iconSvg: 'window',  tagBg: '#fffbeb', tagColor: '#d97706' },
  营销:       { iconBg: 'linear-gradient(135deg, #ef4444, #dc2626)', iconSvg: 'chart',   tagBg: '#fef2f2', tagColor: '#dc2626' },
  营销增长:   { iconBg: 'linear-gradient(135deg, #ef4444, #dc2626)', iconSvg: 'chart',   tagBg: '#fef2f2', tagColor: '#dc2626' },
  视频:       { iconBg: 'linear-gradient(135deg, #ec4899, #be185d)', iconSvg: 'video',   tagBg: '#fdf2f8', tagColor: '#be185d' },
  AI视频:     { iconBg: 'linear-gradient(135deg, #ec4899, #be185d)', iconSvg: 'video',   tagBg: '#fdf2f8', tagColor: '#be185d' },
  私域:       { iconBg: 'linear-gradient(135deg, #14b8a6, #0d9488)', iconSvg: 'people',  tagBg: '#f0fdfa', tagColor: '#0d9488' },
  私域运营:   { iconBg: 'linear-gradient(135deg, #14b8a6, #0d9488)', iconSvg: 'people',  tagBg: '#f0fdfa', tagColor: '#0d9488' },
  AIP:        { iconBg: 'linear-gradient(135deg, #6366f1, #4f46e5)', iconSvg: 'cube',    tagBg: '#eef2ff', tagColor: '#4f46e5' },
  A2A:        { iconBg: 'linear-gradient(135deg, #0ea5e9, #0284c7)', iconSvg: 'link',    tagBg: '#f0f9ff', tagColor: '#0284c7' },
  A2A智能体:  { iconBg: 'linear-gradient(135deg, #0ea5e9, #0284c7)', iconSvg: 'link',    tagBg: '#f0f9ff', tagColor: '#0284c7' },
  编程:       { iconBg: 'linear-gradient(135deg, #475569, #334155)', iconSvg: 'code',    tagBg: '#f1f5f9', tagColor: '#475569' },
  代码:       { iconBg: 'linear-gradient(135deg, #475569, #334155)', iconSvg: 'code',    tagBg: '#f1f5f9', tagColor: '#475569' },
  翻译:       { iconBg: 'linear-gradient(135deg, #0ea5e9, #0284c7)', iconSvg: 'globe',   tagBg: '#f0f9ff', tagColor: '#0284c7' },
  客服:       { iconBg: 'linear-gradient(135deg, #22c55e, #16a34a)', iconSvg: 'people',  tagBg: '#f0fdf4', tagColor: '#16a34a' },
  教育:       { iconBg: 'linear-gradient(135deg, #10b981, #059669)', iconSvg: 'book',    tagBg: '#ecfdf5', tagColor: '#059669' },
  写作:       { iconBg: 'linear-gradient(135deg, #8b5cf6, #7c3aed)', iconSvg: 'sparkle', tagBg: '#f5f3ff', tagColor: '#7c3aed' },
  法律:       { iconBg: 'linear-gradient(135deg, #64748b, #475569)', iconSvg: 'book',    tagBg: '#f1f5f9', tagColor: '#475569' },
  医疗:       { iconBg: 'linear-gradient(135deg, #f43f5e, #e11d48)', iconSvg: 'heart',   tagBg: '#fff1f2', tagColor: '#e11d48' },
  金融:       { iconBg: 'linear-gradient(135deg, #eab308, #ca8a04)', iconSvg: 'chart',   tagBg: '#fefce8', tagColor: '#ca8a04' },
}

/* SVG 路径字典（与 Back 保持一致） */
const svgIconPaths: Record<string, string> = {
  cube:    '<path d="M21 16V8a2 2 0 00-1-1.73l-7-4a2 2 0 00-2 0l-7 4A2 2 0 003 8v8a2 2 0 001 1.73l7 4a2 2 0 002 0l7-4A2 2 0 0021 16z"/><polyline points="3.27 6.96 12 12.01 20.73 6.96"/><line x1="12" y1="22.08" x2="12" y2="12"/>',
  sparkle: '<path d="M12 3l1.912 5.813L20 10l-6.088 1.187L12 17l-1.912-5.813L4 10l6.088-1.187L12 3z"/>',
  book:    '<path d="M4 19.5A2.5 2.5 0 016.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 014 19.5v-15A2.5 2.5 0 016.5 2z"/>',
  chart:   '<line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/>',
  window:  '<rect x="2" y="3" width="20" height="14" rx="2" ry="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/>',
  video:   '<polygon points="23 7 16 12 23 17 23 7"/><rect x="1" y="5" width="15" height="14" rx="2" ry="2"/>',
  people:  '<path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 00-3-3.87"/><path d="M16 3.13a4 4 0 010 7.75"/>',
  link:    '<path d="M10 13a5 5 0 007.54.54l3-3a5 5 0 00-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 00-7.54-.54l-3 3a5 5 0 007.07 7.07l1.71-1.71"/>',
  code:    '<polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/>',
  globe:   '<circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 014 10 15.3 15.3 0 01-4 10 15.3 15.3 0 01-4-10 15.3 15.3 0 014-10z"/>',
  heart:   '<path d="M20.84 4.61a5.5 5.5 0 00-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 00-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 000-7.78z"/>',
}

/* 字符串 → 稳定 hash */
function stableHash(s: string): number {
  let h = 0
  for (let i = 0; i < s.length; i++) {
    h = (h * 31 + s.charCodeAt(i)) | 0
  }
  return Math.abs(h)
}

/* 名称 → 稳定渐变背景色 */
function iconBg(name: string): string {
  const h = stableHash(name) % 360
  return `linear-gradient(135deg, hsl(${h}, 70%, 55%) 0%, hsl(${(h + 40) % 360}, 70%, 48%) 100%)`
}

/* 根据 tag 获取分类样式 */
function getCategoryStyle(tag: string | null | undefined): CategoryStyle {
  if (!tag) return { iconBg: iconBg('agent'), iconSvg: 'cube', tagBg: '#eff6ff', tagColor: '#3b82f6' }
  return categoryStyleMap[tag] || {
    iconBg: iconBg(tag),
    iconSvg: 'cube',
    tagBg: '#eff6ff',
    tagColor: '#3b82f6',
  }
}

/* 计算头像渐变背景与图标 key */
const categoryStyle = computed(() => getCategoryStyle(props.data.tag))
const iconSvgPath = computed(() => svgIconPaths[categoryStyle.value.iconSvg] || svgIconPaths.cube)

/* 使用次数：data.used 数组长度（已使用过该智能体的用户 ID 数）。
   为 0 时按 id 生成一个稳定的伪随机数（10~999），用于无真实统计数据时的展示 */
const useCount = computed(() => {
  const real = props.data.used?.length || 0
  if (real > 0) return real
  const id = String(props.data.id ?? '')
  if (!id) return 0
  let h = 0
  for (let i = 0; i < id.length; i++) {
    h = (h * 31 + id.charCodeAt(i)) | 0
  }
  return 10 + (Math.abs(h) % 990)
})

/* 更新时间格式化 */
const formattedTime = computed(() => {
  if (!props.data.updatedAt) return ''
  const date = new Date(props.data.updatedAt)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const days = Math.floor(diff / 86400000)
  if (days === 0) return '今天'
  if (days === 1) return '昨天'
  if (days < 7) return `${days}天前`
  return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
})

/* 字段映射：数组 -> 数量 */
const toolCount = computed(() => props.data.tool?.length || 0)
const skillCount = computed(() => props.data.hook?.length || 0)
const knowledgeCount = computed(() => props.data.knowledgeBase?.length || 0)
const mcpCount = computed(() => props.data.mcp?.length || 0)

/* 标签配色 */
const tagColors: Record<string, string> = {
  doc: '#5B6B7F',
  multimodal: '#8B6BAF',
  data: '#4A8B7F',
  biz: '#8B7355',
  service: '#5B8B6B',
  industry: '#6B6B8B',
  办公: '#5B7BAF',
  写作: '#8B6BAF',
  文案: '#8B6BAF',
  教育: '#4A8B7F',
  视频: '#8B7355',
  营销: '#5B8B6B',
  客服: '#5B8B6B',
  翻译: '#5B6B7F',
  编程: '#6B6B8B',
  代码: '#6B6B8B',
  法律: '#5B6B7F',
  医疗: '#8B6BAF',
  金融: '#4A8B7F',
}

const getTagStyle = computed(() => {
  const t = props.data.tag
  if (!t) return {}
  const color = tagColors[t.toLowerCase()] || tagColors[t] || '#888'
  return { background: `${color}10`, color }
})

function handleView() {
  emit('view', props.data)
}

function handleTry() {
  if (isDefaultTenant.value) {
    // 默认空间用户：立即试用，新标签页打开聊天窗口
    const routeData = router.resolve({ name: 'Chat', params: { agentId: String(props.data.id) } })
    window.open(routeData.href, '_blank')
  } else {
    // 其他租户：复制到我的空间
    emit('clone', props.data)
  }
}
</script>

<template>
  <div class="agent-card" @click="handleView">
    <!-- 头部：头像 + 名称 + tag/type -->
    <div class="card-header">
      <div
        class="card-avatar"
        :class="data.avatar && !avatarError ? 'avatar-image' : 'avatar-icon'"
        :style="!(data.avatar && !avatarError) ? { background: categoryStyle.iconBg } : undefined"
      >
        <img
          v-if="data.avatar && !avatarError"
          :src="data.avatar"
          :alt="data.name"
          class="avatar-img"
          @error="avatarError = true"
        />
        <svg
          v-else
          width="24" height="24" viewBox="0 0 24 24"
          fill="none" stroke="#fff" stroke-width="2"
          stroke-linecap="round" stroke-linejoin="round"
          v-html="iconSvgPath"
        />
      </div>
      <div class="card-info">
        <div class="card-name">{{ data.name }}</div>
        <div class="card-meta">
          <span v-if="data.tag" :style="getTagStyle" class="meta-tag">{{ data.tag }}</span>
          <span v-if="useCount > 0" class="meta-type">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
            {{ useCount }}
          </span>
        </div>
      </div>
    </div>

    <!-- 描述（最多 2 行） -->
    <p class="card-desc">{{ data.description || '暂无描述' }}</p>

    <!-- 能力 chips -->
    <div v-if="toolCount || skillCount || knowledgeCount || mcpCount" class="card-caps">
      <span v-if="toolCount" class="cap">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z" /></svg>
        {{ toolCount }}
      </span>
      <span v-if="skillCount" class="cap">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2" /><path d="M9 9h6M9 13h6M9 17h4" /></svg>
        {{ skillCount }}
      </span>
      <span v-if="knowledgeCount" class="cap">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" /><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" /></svg>
        {{ knowledgeCount }}
      </span>
      <span v-if="mcpCount" class="cap">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="2" width="8" height="8" rx="1" /><rect x="14" y="2" width="8" height="8" rx="1" /><rect x="2" y="14" width="8" height="8" rx="1" /><rect x="14" y="14" width="8" height="8" rx="1" /></svg>
        {{ mcpCount }}
      </span>
    </div>

    <!-- 底部：试看次数 + 时间 + 体验按钮 -->
    <div class="card-footer">
      <span v-if="trialCount" class="card-trial">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" /><circle cx="12" cy="12" r="3" /></svg>
        {{ trialCount }}
      </span>
      <span v-if="formattedTime" class="card-time">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10" /><path d="M12 6v6l4 2" /></svg>
        {{ formattedTime }}
      </span>
      <button class="try-btn" type="button" @click.stop="handleTry">
        {{ isDefaultTenant ? '立即试用' : '复制到我的空间' }}
        <svg v-if="!isDefaultTenant" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12h14"/></svg>
        <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="5 3 19 12 5 21 5 3"/></svg>
      </button>
    </div>
  </div>
</template>

<style scoped lang="scss">
/* ===================== 卡片容器 ===================== */
.agent-card {
  position: relative;
  background: #fff;
  border-radius: 18px;
  padding: 22px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border: 1px solid #f1f5f9;
  display: flex;
  flex-direction: column;
  gap: 14px;
  overflow: hidden;

  /* 悬停时的渐变装饰 */
  &::before {
    content: '';
    position: absolute;
    inset: 0;
    border-radius: 18px;
    background: linear-gradient(135deg, rgba(59, 130, 246, 0.03) 0%, rgba(139, 92, 246, 0.02) 100%);
    opacity: 0;
    transition: opacity 0.3s;
    pointer-events: none;
  }

  &:hover {
    transform: translateY(-6px);
    border-color: rgba(59, 130, 246, 0.3);
    box-shadow: 0 8px 28px rgba(15, 23, 42, 0.08), 0 4px 20px rgba(59, 130, 246, 0.06);

    &::before {
      opacity: 1;
    }
  }
}

/* ===================== 头部：头像 + 名称 ===================== */
.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  position: relative;
  z-index: 1;
}

.card-avatar {
  width: 52px;
  height: 52px;
  border-radius: 16px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  overflow: hidden;
  box-shadow: inset 0 2px 6px rgba(0, 0, 0, 0.08);

  .avatar-img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  /* 图片头像：白底 */
  &.avatar-image {
    background: #fff;
  }
  /* 渐变头像：背景由 :style 注入 */
  &.avatar-icon {
    color: #fff;
  }
}

.card-info {
  flex: 1;
  min-width: 0;
}

.card-name {
  font-size: 17px;
  font-weight: 700;
  color: #1e293b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 6px;
}

.card-meta {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;

  .meta-tag {
    padding: 2px 8px;
    border-radius: 6px;
    font-size: 11px;
    font-weight: 500;
  }

  .meta-type {
    display: inline-flex;
    align-items: center;
    gap: 3px;
    padding: 2px 8px;
    border-radius: 6px;
    font-size: 11px;
    font-weight: 600;
    background: #eff6ff;
    color: #2563eb;

    svg { width: 11px; height: 11px; stroke: #2563eb; }
  }
}

/* ===================== 描述 ===================== */
.card-desc {
  font-size: 13px;
  color: #64748b;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  margin: 0;
  flex: 1;
  position: relative;
  z-index: 1;
}

/* ===================== 能力 chips ===================== */
.card-caps {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  position: relative;
  z-index: 1;

  .cap {
    display: inline-flex;
    align-items: center;
    gap: 3px;
    padding: 3px 8px;
    border-radius: 4px;
    font-size: 11px;
    font-weight: 500;
    background: #f5f5f5;
    color: #888;

    svg { width: 12px; height: 12px; stroke: #aaa; }
  }
}

/* ===================== 底部 ===================== */
.card-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  padding-top: 10px;
  border-top: 1px solid #f0f0f0;
  position: relative;
  z-index: 1;

  .card-trial,
  .card-time {
    font-size: 12px;
    color: #94a3b8;
    display: inline-flex;
    align-items: center;
    gap: 3px;

    svg { width: 12px; height: 12px; }
  }

  .try-btn {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 6px 12px;
    border: 1px solid #cfe0f9;
    border-radius: 8px;
    background: #f5f9ff;
    color: #2563eb;
    font-size: 12px;
    font-weight: 600;
    white-space: nowrap;
    cursor: pointer;
    transition: all 0.2s ease;
    margin-left: auto;

    svg { width: 14px; height: 14px; }

    &:hover {
      background: #2563eb;
      color: #fff;
      border-color: #2563eb;
      transform: scale(1.02);
    }
  }
}
</style>
