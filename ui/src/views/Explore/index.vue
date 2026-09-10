<!--
  v2 门户智能体广场
  核心功能：分类侧栏 + 搜索 + 排序 + 网格/列表 + 智能体卡片
  接口：复用本系统 @/api/agent

  @author huxuehao
-->
<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { RoutePaths } from '@/router/constants'
import {
  AppstoreOutlined,
  BookOutlined,
  BulbOutlined,
  DesktopOutlined,
  FileTextOutlined,
  RiseOutlined,
  TeamOutlined,
  UnorderedListOutlined,
  SearchOutlined,
  RobotOutlined,
  PlusOutlined,
  VideoCameraOutlined,
} from '@ant-design/icons-vue'
import * as v2PortalApi from '@/api/v2Portal'
import type { AgentDefinitionVO } from '@/types'
import { useAccountStore } from '@/stores'
import { runCloneAgentFlow } from '@/composables/useAgentCloneFlow'
import { isPortalV3, isPortalV4, isPortalV5 } from '@/config/portal'
import MarketplaceV4 from './MarketplaceV4.vue'
import MarketplaceV5 from './MarketplaceV5.vue'
import AgentCard from './components/AgentCard.vue'
import AgentDetailModal from './components/AgentDetailModal.vue'
import { ToolbarV2 } from '@/components/layout-v2'
import type { Component } from 'vue'

/* ===================== 基础引用 ===================== */
const route = useRoute()
const router = useRouter()
const accountStore = useAccountStore()

/* ===================== 状态 ===================== */
const query = ref('')
const category = ref('全部智能体')
const tab = ref<'recommend' | 'latest' | 'popular' | 'rating'>('recommend')
const view = ref<'grid' | 'list'>('grid')

/** 从 URL query 恢复搜索词 */
if (route.query.q) {
  query.value = String(route.query.q)
}

watch(() => route.query.q, (newQ) => {
  const q = newQ ? String(newQ) : ''
  if (q !== query.value) {
    query.value = q
    loadAgents(1)
  }
})

/* ===================== 数据 ===================== */
const agents = ref<AgentDefinitionVO[]>([])
const tags = ref<string[]>([])
const loading = ref(false)
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(12)
const hasMore = ref(true)
/** debounce 定时器 */
let searchTimer: ReturnType<typeof setTimeout> | null = null

/* ===================== 分类侧栏 ===================== */
const FIXED_PORTAL_CATEGORIES = [
  'AI应用',
  '文案写作',
  '内容生成',
  'AI教育',
  '内容运营',
  '办公提效',
  '营销增长',
  'AI视频',
  '私域运营',
  'AIP',
]

const categories = computed(() => {
  const fixed = [{ name: '全部智能体', count: String(total.value) }]
  return [
    ...fixed,
    ...FIXED_PORTAL_CATEGORIES.map((name) => ({ name, count: '' })),
  ]
})

/** 标签 → 图标映射 */
const tagIconMap: Record<string, string> = {
  A2A: 'link',
  办公: 'window',
  写作: 'sparkle',
  文案: 'sparkle',
  教育: 'book',
  视频: 'video',
  营销: 'chart',
  客服: 'service',
  翻译: 'globe',
  编程: 'code',
  代码: 'code',
  法律: 'file',
  医疗: 'heart',
  金融: 'chart',
}

function getTagIcon(tag: string): string {
  for (const [keyword, icon] of Object.entries(tagIconMap)) {
    if (tag.includes(keyword)) return icon
  }
  return 'tag'
}

function getCategoryIcon(name: string): Component {
  if (name === '全部智能体') return AppstoreOutlined
  if (name.includes('文案') || name.includes('内容生成')) return FileTextOutlined
  if (name.includes('教育')) return BookOutlined
  if (name.includes('视频')) return VideoCameraOutlined
  if (name.includes('营销') || name.includes('运营')) return RiseOutlined
  if (name.includes('办公')) return DesktopOutlined
  if (name.includes('私域')) return TeamOutlined
  if (name.includes('应用') || name === 'AIP') return RobotOutlined
  return BulbOutlined
}

function getCategoryColor(name: string): string {
  if (name === '全部智能体') return '#2563eb'
  if (name.includes('文案') || name.includes('内容生成')) return '#8b5cf6'
  if (name.includes('教育')) return '#f59e0b'
  if (name.includes('视频')) return '#06b6d4'
  if (name.includes('营销')) return '#ec4899'
  if (name.includes('内容运营')) return '#10b981'
  if (name.includes('办公')) return '#3b82f6'
  if (name.includes('私域')) return '#14b8a6'
  if (name.includes('应用') || name === 'AIP') return '#6366f1'
  return '#64748b'
}

/* ===================== 排序 ===================== */
const SORT_OPTIONS: Array<{ value: typeof tab.value; label: string }> = [
  { value: 'recommend', label: '综合推荐' },
  { value: 'latest', label: '最新发布' },
  { value: 'popular', label: '最多使用' },
  { value: 'rating', label: '最高评分' },
]

/* ===================== 加载数据 ===================== */
async function loadTags() {
  try {
    const res = await v2PortalApi.listTagsByPortal()
    tags.value = res.data.data || []
  } catch (e) {
    console.error('加载标签失败', e)
  }
}

async function loadAgents(page = 1, append = false) {
  try {
    loading.value = true
    const selectedTag = category.value === '全部智能体'
      ? undefined
      : category.value
    const res = await v2PortalApi.pageByPortal({
      page,
      size: pageSize.value,
      name: query.value || undefined,
      tag: selectedTag,
    })
    const result = res.data.data || ({} as any)
    const records: AgentDefinitionVO[] = result.records || []
    if (append) {
      agents.value.push(...records)
    } else {
      agents.value = records
    }
    /* 客户端按 tab 做本地排序（后端暂不支持） */
    sortAgents()
    total.value = result.total || 0
    currentPage.value = page
    hasMore.value = agents.value.length < total.value
  } catch (e) {
    console.error('加载智能体失败', e)
  } finally {
    loading.value = false
  }
}

/**
 * 按当前 tab 对 agents 做本地排序
 * - recommend: 保持后端默认顺序
 * - latest:    按 updatedAt 降序
 * - popular:   按 used.length 降序
 * - rating:    按 used.length 降序（无评分字段时的等价近似）
 */
function sortAgents() {
  if (tab.value === 'recommend' || agents.value.length === 0) return
  const getTime = (a: AgentDefinitionVO) => {
    const t = a.updatedAt ? new Date(a.updatedAt).getTime() : 0
    return Number.isFinite(t) ? t : 0
  }
  const getUse = (a: AgentDefinitionVO) => a.used?.length || 0
  const sorted = [...agents.value].sort((a, b) => {
    if (tab.value === 'latest') return getTime(b) - getTime(a)
    if (tab.value === 'popular' || tab.value === 'rating') return getUse(b) - getUse(a)
    return 0
  })
  agents.value = sorted
}

/** 触底加载 */
async function loadMore() {
  if (loading.value || !hasMore.value) return
  await loadAgents(currentPage.value + 1, true)
}

/* ===================== 事件 ===================== */
function onCategorySelect(name: string) {
  category.value = name
  loadAgents(1)
}

function onSearchEnter() {
  if (searchTimer) {
    clearTimeout(searchTimer)
    searchTimer = null
  }
  loadAgents(1)
}

/** 中文输入法结束（拼音上屏后）触发一次搜索 */
function onCompositionEnd() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    searchTimer = null
    loadAgents(1)
  }, 400)
}

/** 清空搜索词并重新加载 */
function onClearSearch() {
  query.value = ''
  onSearchEnter()
}

function onSortSelect(value: typeof tab.value) {
  if (tab.value === value) return
  tab.value = value
  loadAgents(1)
}

function onCreateAgent() {
  router.push(`/${RoutePaths.AGENT}`)
}

function onOpenWorkspace() {
  router.push(`/${RoutePaths.AGENT}`)
}

/* ===================== 详情弹窗 ===================== */
const detailOpen = ref(false)
const detailAgent = ref<AgentDefinitionVO | null>(null)

function onAgentView(agent: AgentDefinitionVO) {
  detailAgent.value = agent
  detailOpen.value = true
}

/**
 * 卡片"复制到我的空间"按钮：弹确认框 → 克隆 → 跳编辑页
 */
async function onAgentClone(agent: AgentDefinitionVO) {
  await runCloneAgentFlow({
    sourceAgentId: agent.id,
    sourceName: agent.name,
    onSuccess: (data) => {
      // 跳转到新 agent 的编辑页（让用户配置模型等）
      router.push({ name: 'Agent', params: { id: data.id } })
    },
  })
}

function onCloneSuccess(newAgentId: string) {
  // 详情弹窗克隆成功后由 modal 自己处理跳转，这里仅预留扩展点
  console.log('agent cloned success, new id:', newAgentId)
}

/* ===================== 初始化 ===================== */
onMounted(async () => {
  if (isPortalV5) return
  await loadTags()
  await loadAgents(1)
})

onUnmounted(() => {
  if (searchTimer) {
    clearTimeout(searchTimer)
    searchTimer = null
  }
})
</script>

<template>
  <MarketplaceV5 v-if="isPortalV5" />

  <MarketplaceV4 v-else-if="isPortalV4" />

  <div v-else-if="isPortalV3" class="explore-v3-page">
    <aside class="explore-v3-sidebar">
      <div class="explore-v3-sidebar-head">
        <span>智能体分类</span>
      </div>
      <nav class="explore-v3-sidebar-list" aria-label="智能体分类">
        <button
          v-for="cat in categories"
          :key="cat.name"
          type="button"
          :class="{ active: category === cat.name }"
          @click="onCategorySelect(cat.name)"
        >
          <component :is="getCategoryIcon(cat.name)" :style="{ color: getCategoryColor(cat.name) }" />
          <span>{{ cat.name }}</span>
          <small>{{ cat.count || '' }}</small>
        </button>
      </nav>
      <ToolbarV2 class="explore-v3-sidebar-tools" />
    </aside>

    <main class="explore-v3-main">
      <section class="explore-v3-hero">
        <video
          class="explore-v3-hero-video"
          autoplay
          loop
          muted
          playsinline
          preload="metadata"
          poster="/media/flowing-blue-waves-poster.jpg"
          aria-hidden="true"
        >
          <source src="/media/flowing-blue-waves.mp4" type="video/mp4" />
        </video>
        <div class="explore-v3-hero-copy">
          <span class="explore-v3-eyebrow">星图 AI · 智能体广场</span>
          <h1>探索并使用适合你工作场景的智能体</h1>
          <div class="explore-v3-search">
            <SearchOutlined />
            <input
              v-model="query"
              type="text"
              placeholder="搜索：文档总结、数据分析、PPT 制作、会议纪要……"
              @keydown.enter="onSearchEnter"
              @compositionend="onCompositionEnd"
            />
            <button type="button" :disabled="loading" @click="onSearchEnter">搜索</button>
          </div>
        </div>
        <div class="explore-v3-actions">
          <button class="explore-v3-create" type="button" @click="onCreateAgent">
            <span class="explore-v3-create-icon"><RobotOutlined /></span>
            <span>
              <strong>定制智能体</strong>
              <small>满足个性化业务需求</small>
            </span>
            <PlusOutlined />
          </button>
          <button class="explore-v3-create explore-v3-workspace" type="button" @click="onOpenWorkspace">
            <span class="explore-v3-create-icon"><AppstoreOutlined /></span>
            <span>
              <strong>我的专属空间</strong>
              <small>管理智能体与扩展能力</small>
            </span>
            <span class="explore-v3-enter">→</span>
          </button>
        </div>
      </section>

      <section class="explore-v3-toolbar">
        <div class="explore-v3-tabs">
          <button
            v-for="opt in SORT_OPTIONS"
            :key="opt.value"
            type="button"
            :class="{ active: tab === opt.value }"
            @click="onSortSelect(opt.value)"
          >{{ opt.label }}</button>
        </div>
        <div class="view-switch">
          <button :class="['view-btn', { active: view === 'grid' }]" type="button" @click="view = 'grid'">
            <AppstoreOutlined />
          </button>
          <button :class="['view-btn', { active: view === 'list' }]" type="button" @click="view = 'list'">
            <UnorderedListOutlined />
          </button>
        </div>
      </section>

      <section class="explore-v3-content">
        <div v-if="loading && agents.length === 0" class="loading-state">
          <div class="loading-spinner"></div>
          <p>正在加载智能体...</p>
        </div>
        <div v-else-if="agents.length === 0" class="empty-state">
          <RobotOutlined class="empty-icon" />
          <p>暂无智能体</p>
          <span class="empty-tip">尝试调整分类或搜索词</span>
        </div>
        <div v-else :class="['agent-grid', `agent-grid-${view}`]">
          <AgentCard
            v-for="agent in agents"
            :key="agent.id"
            :data="agent"
            @view="onAgentView"
            @clone="onAgentClone"
          />
        </div>
        <div v-if="hasMore && agents.length > 0" class="load-more-wrap">
          <button class="load-more-btn" type="button" :disabled="loading" @click="loadMore">
            {{ loading ? '加载中...' : '加载更多' }}
          </button>
        </div>
      </section>
    </main>

    <AgentDetailModal
      v-model:open="detailOpen"
      :agent="detailAgent"
      @clone-success="onCloneSuccess"
    />
  </div>

  <div v-else class="explore-page">
    <!-- Row 1: Hero + 创建智能体 -->
    <div class="explore-top-row">
      <section class="explore-hero">
        <div class="hero-content">
          <h1 class="hero-title">探索智能体 · 让 AI 成为你的协作伙伴</h1>
          <p class="hero-subtitle">在 {{ total }} 个智能体中，发现适合你的那位搭档</p>
        </div>
      </section>
      <div class="explore-create-area">
        <button class="create-agent-btn" type="button" @click="onCreateAgent">
          <PlusOutlined />
          <span>创建智能体</span>
        </button>
      </div>
    </div>

    <!-- Row 2: 工具栏（检索 + 综合推荐 + 显示方式 + 分类导航） -->
    <section class="explore-toolbar">
      <div class="search-bar">
        <SearchOutlined class="search-icon" />
        <input
          v-model="query"
          type="text"
          placeholder="按名称、标签搜索智能体..."
          @keydown.enter="onSearchEnter"
          @compositionend="onCompositionEnd"
        />
        <button
          v-if="query"
          type="button"
          class="search-clear"
          title="清空"
          @click="onClearSearch"
        >×</button>
        <button
          type="button"
          class="search-go"
          :disabled="loading"
          @click="onSearchEnter"
        >搜索</button>
      </div>

      <div class="toolbar-right">
        <!-- 排序切换（直接显示） -->
        <div class="sort-switch">
          <button
            v-for="opt in SORT_OPTIONS"
            :key="opt.value"
            :class="['sort-switch-btn', { active: tab === opt.value }]"
            type="button"
            @click="onSortSelect(opt.value)"
          >
            {{ opt.label }}
          </button>
        </div>

        <!-- 视图切换 -->
        <div class="view-switch">
          <button
            :class="['view-btn', { active: view === 'grid' }]"
            type="button"
            @click="view = 'grid'"
          >
            <AppstoreOutlined />
          </button>
          <button
            :class="['view-btn', { active: view === 'list' }]"
            type="button"
            @click="view = 'list'"
          >
            <UnorderedListOutlined />
          </button>
        </div>

      </div>
    </section>

    <!-- Row 3: 分类标签（占满页面宽度） -->
    <section class="explore-categories">
      <ul class="category-list">
        <li
          v-for="cat in categories"
          :key="cat.name"
          :class="['category-item', { active: category === cat.name }]"
          @click="onCategorySelect(cat.name)"
        >
          <span class="cat-name">{{ cat.name }}</span>
          <span v-if="cat.count" class="cat-count">{{ cat.count }}</span>
        </li>
      </ul>
    </section>

    <!-- Row 4: 智能体列表/网格 -->
    <section class="explore-content">
      <div v-if="loading && agents.length === 0" class="loading-state">
        <div class="loading-spinner"></div>
        <p>正在加载智能体...</p>
      </div>

      <div v-else-if="agents.length === 0" class="empty-state">
        <RobotOutlined class="empty-icon" />
        <p>暂无智能体</p>
        <span class="empty-tip">尝试调整分类或搜索词</span>
      </div>

      <div v-else :class="['agent-grid', `agent-grid-${view}`]">
        <AgentCard
          v-for="agent in agents"
          :key="agent.id"
          :data="agent"
          @view="onAgentView"
          @clone="onAgentClone"
        />
      </div>

      <!-- 加载更多 -->
      <div v-if="hasMore && agents.length > 0" class="load-more-wrap">
        <button class="load-more-btn" type="button" :disabled="loading" @click="loadMore">
          {{ loading ? '加载中...' : '加载更多' }}
        </button>
      </div>
    </section>

    <!-- 详情弹窗 -->
    <AgentDetailModal
      v-model:open="detailOpen"
      :agent="detailAgent"
      @clone-success="onCloneSuccess"
    />
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/explore/index' as *;
@use '@/styles/explore/v3' as *;
</style>
