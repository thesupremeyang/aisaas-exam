<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  ArrowLeftOutlined,
  ArrowRightOutlined,
  DownOutlined,
  CloseOutlined,
  LoadingOutlined,
  PlusOutlined,
  SearchOutlined,
  TeamOutlined,
} from '@ant-design/icons-vue'
import * as v2PortalApi from '@/api/v2Portal'
import type { AgentDefinitionVO } from '@/types'
import Chat from '@/views/Chat/index.vue'

const CREATOR_AGENT_ID = '2072928698495160322'
const publicAsset = (path: string) => `${import.meta.env.BASE_URL}${path.replace(/^\//, '')}`
const categories = ['全部', '内容创作', '数据分析', '办公提效', '营销增长', '教育学习', '法律咨询', '行业研究', '产品设计', '技术开发']
const scenes = [
  { name: '内容创作', image: publicAsset('assets/scene-content-v2.png'), agents: ['内容创作专家团', '内容创作专家', '小红书运营专家'] },
  { name: '数据分析', image: publicAsset('assets/scene-data-v2.png'), agents: ['数据分析报告师', '高级数据分析师', '深度研究团队'] },
  { name: '办公提效', image: publicAsset('assets/scene-office-v2.png'), agents: ['周报日报生成器', '会议效率专家', 'PDF文档处理专家'] },
  { name: '营销增长', image: publicAsset('assets/scene-growth-v2.png'), agents: ['营销增长专家', '私域运营专家', '文案写作专家'] },
  { name: '教育学习', image: publicAsset('assets/scene-learning-v2.png'), agents: ['长文档写作专家', '编程助手', 'AI教育专家'] },
]
const sceneDescriptions: Record<string, string[]> = {
  内容创作: ['协同完成选题、资料整理、写作和多平台内容改编。', '根据目标受众生成文章、脚本与社交媒体文案。', '规划小红书选题，优化标题、正文结构和发布节奏。'],
  数据分析: ['接入数据后自动完成清洗、分析并生成可视化报告。', '定位关键指标变化，输出业务洞察与决策建议。', '围绕复杂问题检索资料、交叉验证并形成研究结论。'],
  办公提效: ['汇总工作记录，一键生成结构清晰的日报、周报和月报。', '整理会议录音与笔记，提取结论、待办和负责人。', '完成 PDF 合并、拆分、OCR 识别、摘要与格式转换。'],
  营销增长: ['制定渠道策略、活动方案和增长实验，持续跟踪效果。', '设计社群运营节奏，沉淀用户标签与精细化触达方案。', '根据品牌语气撰写广告、落地页和营销活动文案。'],
  教育学习: ['辅助长文档搭建结构、续写润色并检查表达一致性。', '讲解代码、定位错误，并给出循序渐进的修改建议。', '生成课程大纲、练习题和个性化学习反馈。'],
}

const prompt = ref('')
const query = ref('')
const category = ref('全部')
const sort = ref<'popular' | 'latest'>('popular')
const agents = ref<AgentDefinitionVO[]>([])
const loading = ref(false)
const drawerOpen = ref(false)
const chatKey = ref(0)
const initialPrompt = ref('')
const sceneScroller = ref<HTMLElement | null>(null)
const expandedScene = ref<(typeof scenes)[number] | null>(null)
const scenePhase = ref<'origin' | 'open' | 'closing'>('origin')
let sceneCloseTimer: ReturnType<typeof setTimeout> | null = null

const filteredAgents = computed(() => agents.value.filter((agent) => {
  const text = `${agent.name || ''}${agent.description || ''}${agent.tag || ''}`
  const matchesQuery = !query.value.trim() || text.includes(query.value.trim())
  const matchesCategory = category.value === '全部' || text.includes(category.value)
  return matchesQuery && matchesCategory
}))

async function loadAgents() {
  loading.value = true
  try {
    const response = await v2PortalApi.pageByPortal({ current: 1, size: 20 } as never)
    agents.value = response.data?.data?.records || []
  } finally {
    loading.value = false
  }
}

function startCreate(text = prompt.value) {
  initialPrompt.value = text.trim()
  chatKey.value += 1
  drawerOpen.value = true
}

function openScene(scene: (typeof scenes)[number]) {
  expandedScene.value = scene
  scenePhase.value = 'origin'
  requestAnimationFrame(() => requestAnimationFrame(() => { scenePhase.value = 'open' }))
}

function closeScene() {
  if (!expandedScene.value || scenePhase.value === 'closing') return
  scenePhase.value = 'closing'
  sceneCloseTimer = setTimeout(() => {
    expandedScene.value = null
    scenePhase.value = 'origin'
  }, 420)
}

function avatarUrl(seed: string, colors = 'e8eaff') {
  return `https://api.dicebear.com/9.x/notionists/png?seed=${encodeURIComponent(seed)}&backgroundColor=${colors}`
}

onMounted(() => {
  loadAgents()
  window.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  if (sceneCloseTimer) clearTimeout(sceneCloseTimer)
})
function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') closeScene()
}
</script>

<template>
  <div :class="['marketplace-v4', { 'drawer-open': drawerOpen }]">
    <div class="v4-surface">
      <header class="v4-topbar">
        <div class="v4-brand">
          <img :src="publicAsset('assets/xingtu-logo.png')" alt="星图AI" />
          <strong>星图AI智能体</strong>
        </div>
        <nav><span>智能体广场</span></nav>
        <div class="v4-top-actions">
          <label><SearchOutlined /><input v-model="query" placeholder="搜索智能体名称或能力" /></label>
          <a class="v4-mine" href="http://localhost:3030/web/#/agent">我的智能体</a>
          <span class="v4-avatar">A</span>
        </div>
      </header>

      <main class="v4-main">
        <div class="v4-discovery">
          <video autoplay muted loop playsinline :poster="publicAsset('media/flowing-blue-waves-poster.jpg')">
            <source :src="publicAsset('media/flowing-blue-waves.mp4')" type="video/mp4" />
          </video>
          <div class="v4-wash" />
          <section class="v4-create-strip">
            <img :src="publicAsset('assets/xiaotu-blue-v3.png')" alt="小图" />
            <div><h1>一句话，创建你的专属智能体</h1><p>告诉小图你想让智能体做什么</p></div>
            <label>
              <input v-model="prompt" placeholder="例如：创建一个每天整理行业新闻并生成简报的智能体" @keydown.enter="startCreate()" />
              <button type="button" @click="startCreate()">开始创建 <ArrowRightOutlined /></button>
            </label>
          </section>

          <section class="v4-featured">
            <header><h2>精选场景</h2><div><button @click="sceneScroller?.scrollBy({ left: -420, behavior: 'smooth' })"><ArrowLeftOutlined /></button><button @click="sceneScroller?.scrollBy({ left: 420, behavior: 'smooth' })"><ArrowRightOutlined /></button></div></header>
            <div ref="sceneScroller" class="v4-scenes">
              <article v-for="scene in scenes" :key="scene.name" role="button" tabindex="0" @click="openScene(scene)" @keydown.enter="openScene(scene)">
                <img :src="scene.image" alt="" />
                <div><h3>{{ scene.name }}</h3><p v-for="(name, index) in scene.agents" :key="name"><img :src="avatarUrl(`${name}-${index}`)" alt="" />{{ name }}</p></div>
              </article>
            </div>
          </section>
        </div>

        <section class="v4-agents">
          <h2>智能体</h2>
          <div class="v4-filters">
            <div><button v-for="item in categories" :key="item" :class="{ active: category === item }" @click="category = item">{{ item }}</button><button>更多 <DownOutlined /></button></div>
            <aside><button :class="{ active: sort === 'popular' }" @click="sort = 'popular'">最热</button><button :class="{ active: sort === 'latest' }" @click="sort = 'latest'">最新</button></aside>
          </div>
          <div v-if="loading" class="v4-empty">正在加载智能体…</div>
          <div v-else-if="!filteredAgents.length" class="v4-empty">没有找到匹配的智能体</div>
          <div v-else class="v4-grid">
            <article v-for="agent in filteredAgents" :key="agent.id" class="v4-agent-card">
              <div class="v4-agent-title">
                <img :src="agent.avatar || avatarUrl(String(agent.name), 'f5d0a9,c0aede,b6e3f4')" alt="" />
                <div><h3>{{ agent.name }}</h3><span>{{ agent.tag || '智能体' }}</span></div>
              </div>
              <p>{{ agent.description || '暂无描述' }}</p>
              <div class="v4-card-foot">
                <span><TeamOutlined /> {{ agent.used?.length || 0 }} 人使用</span>
                <button type="button">复制到我的空间 <PlusOutlined /></button>
              </div>
            </article>
          </div>
        </section>
      </main>
    </div>

    <template v-if="expandedScene">
      <button :class="['v4-scene-backdrop', scenePhase]" aria-label="关闭场景详情" @click="closeScene" />
      <section :class="['v4-scene-folder', scenePhase]" role="dialog" aria-modal="true">
        <header>
          <img :src="expandedScene.image" alt="" />
          <div><span>精选场景</span><h2>{{ expandedScene.name }}</h2><p>为这个工作场景准备的智能体</p></div>
          <button type="button" aria-label="关闭" @click="closeScene"><CloseOutlined /></button>
        </header>
        <div class="v4-scene-folder-body">
          <div class="v4-scene-folder-label"><strong>智能体</strong><span>{{ expandedScene.agents.length }} 个</span></div>
          <article v-for="(name, index) in expandedScene.agents" :key="name">
            <img :src="avatarUrl(`${name}-folder`)" alt="" />
            <div><span>{{ expandedScene.name }}</span><h3>{{ name }}</h3><p>{{ sceneDescriptions[expandedScene.name]?.[index] || '' }}</p></div>
          </article>
        </div>
      </section>
    </template>

    <aside class="v4-drawer" :aria-hidden="!drawerOpen">
      <header>
        <div><img :src="publicAsset('assets/xiaotu-blue-v3.png')" alt="" /><span><strong>小图 · 创建智能体</strong><small>用自然语言完成智能体配置</small></span></div>
        <button type="button" aria-label="关闭" @click="drawerOpen = false"><CloseOutlined /></button>
      </header>
      <div class="v4-chat">
        <Chat
          :key="chatKey"
          :chat-agent-id="CREATOR_AGENT_ID"
          :show-account="false"
          :initial-prompt="initialPrompt"
          welcome-headline="你好，我是小图"
          welcome-description="我会一步步帮你梳理需求。配置确认一次后，剩下的创建与部署都交给我。"
          :starter-questions-override="[
            '我想创建一个智能体，但还不知道该怎么描述',
            '谁会使用它？最希望它解决哪三件事？',
            '它通常接收什么输入，需要输出什么结果？',
          ]"
          portal-agent
          compact
        />
      </div>
      <div class="v4-running-note"><LoadingOutlined /><span>执行过程已隐藏；确认配置后将自动创建并部署</span></div>
    </aside>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/explore/v4' as *;
</style>
