<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  AppstoreOutlined,
  ArrowLeftOutlined,
  ArrowRightOutlined,
  CloseOutlined,
  DownOutlined,
  PlusOutlined,
  HistoryOutlined,
  SearchOutlined,
  SettingOutlined,
  TeamOutlined,
  LogoutOutlined,
} from '@ant-design/icons-vue'
import { Modal } from 'ant-design-vue'
import * as v2PortalApi from '@/api/v2Portal'
import type { AgentDefinitionVO } from '@/types'
import { RouteNames, RoutePaths } from '@/router/constants'
import { runCloneAgentFlow } from '@/composables/useAgentCloneFlow'
import { useAccountStore } from '@/stores'
import { isPlatformAdminUsername } from '@/utils/platformAdmin'
import SettingsModal from '@/components/settings/SettingsModal.vue'
import Chat from '@/views/Chat/index.vue'

type SceneConfig = {
  name: string
  image: string
}

type Scene = SceneConfig & {
  agents: AgentDefinitionVO[]
}

type SceneMotion = {
  x: number
  y: number
  scaleX: number
  scaleY: number
  finalLeft: number
  finalTop: number
  finalWidth: number
  finalHeight: number
}

type ExpandedScene = {
  scene: Scene
  index: number
  motion: SceneMotion
}

const router = useRouter()
const accountStore = useAccountStore()
const creatorAgentId = ref('')
const publicAsset = (path: string) => `${import.meta.env.BASE_URL}${path.replace(/^\//, '')}`
const ALL_CATEGORY = '全部'

const sceneConfigs: SceneConfig[] = [
  { name: '内容创作', image: publicAsset('assets/scene-content-v2.png') },
  { name: '数据分析', image: publicAsset('assets/scene-data-v2.png') },
  { name: '办公提效', image: publicAsset('assets/scene-office-v2.png') },
  { name: '营销增长', image: publicAsset('assets/scene-growth-v2.png') },
  { name: '教育学习', image: publicAsset('assets/scene-learning-v2.png') },
]

const prototypeCategories = ['内容创作', '数据分析', '办公提效', '营销增长', '教育学习', '法律咨询', '行业研究', '产品设计', '技术开发']
const category = ref(ALL_CATEGORY)
const search = ref('')
const sort = ref<'popular' | 'latest'>('popular')
const prompt = ref('')
const drawerOpen = ref(false)
const chatKey = ref(0)
const creatorChatRef = ref<InstanceType<typeof Chat> | null>(null)
const initialPrompt = ref('')
const agents = ref<AgentDefinitionVO[]>([])
const backendTags = ref<string[]>([])
const loading = ref(false)
const expandedSceneAgents = ref<AgentDefinitionVO[]>([])
const expandedSceneLoading = ref(false)
const expandedSceneError = ref('')
const settingsVisible = ref(false)
const expandedScene = ref<ExpandedScene | null>(null)
const scenePhase = ref<'origin' | 'open' | 'closing'>('origin')
const scroller = ref<HTMLElement | null>(null)
let sceneCloseTimer: ReturnType<typeof setTimeout> | null = null
let sceneRequestSeq = 0

const categories = computed(() => {
  const merged = new Set([ALL_CATEGORY, ...prototypeCategories])
  backendTags.value.forEach((tag) => {
    if (tag) merged.add(tag)
  })
  return Array.from(merged)
})

const scenes = computed<Scene[]>(() =>
  sceneConfigs.map((scene) => ({
    ...scene,
    agents: agents.value
      .filter((agent) => agent.enabled !== false && agent.tag === scene.name)
      .sort((a, b) => getUseCount(b) - getUseCount(a))
      .slice(0, 3),
  })),
)

const userInitial = computed(() =>
  accountStore.userInfo?.username?.charAt(0).toUpperCase() || '登',
)

const userLabel = computed(() =>
  accountStore.isLoggedIn
    ? accountStore.userInfo?.nickname || accountStore.userInfo?.username || '账户'
    : '登录',
)
const isPlatformAdmin = computed(
  () => isPlatformAdminUsername(accountStore.userInfo?.username),
)

const isDefaultTenant = computed(() => {
  const tenant = accountStore.currentTenant
  return String(tenant?.tenantId ?? '') === '1' || tenant?.tenantCode === 'default'
})

const accountMenuItems = computed(() =>
  accountStore.isLoggedIn
    ? [
        { key: 'settings', label: '系统设置', icon: () => h(SettingOutlined) },
        { type: 'divider' },
        { key: 'logout', danger: true, label: '退出登录', icon: () => h(LogoutOutlined) },
      ]
    : [{ key: 'login', label: '登录' }],
)

const filteredAgents = computed(() => {
  const keyword = search.value.trim()
  const rows = agents.value.filter((agent) => {
    const text = `${agent.name || ''}${agent.description || ''}${agent.tag || ''}`
    const matchesSearch = !keyword || text.includes(keyword)
    const matchesCategory = category.value === ALL_CATEGORY || text.includes(category.value)
    return matchesSearch && matchesCategory
  })

  return [...rows].sort((a, b) => {
    if (sort.value === 'latest') {
      return getTime(b.updatedAt) - getTime(a.updatedAt)
    }
    return getUseCount(b) - getUseCount(a)
  })
})

function getTime(value?: string) {
  const time = value ? new Date(value).getTime() : 0
  return Number.isFinite(time) ? time : 0
}

function getUseCount(agent: AgentDefinitionVO) {
  return agent.used?.length || 0
}

function avatarUrl(seed: string, colors = 'e8eaff') {
  return `https://api.dicebear.com/9.x/notionists/png?seed=${encodeURIComponent(seed)}&backgroundColor=${colors}`
}

async function loadTags() {
  try {
    const response = await v2PortalApi.listTagsByPortal()
    backendTags.value = response.data?.data || []
  } catch (error) {
    console.error('加载智能体标签失败', error)
  }
}

async function loadAgents() {
  loading.value = true
  try {
    const response = await v2PortalApi.pageByPortal({
      page: 1,
      size: 100,
    })
    agents.value = response.data?.data?.records || []
  } catch (error) {
    console.error('加载智能体失败', error)
    agents.value = []
  } finally {
    loading.value = false
  }
}

async function loadExpandedSceneAgents(sceneName: string) {
  const requestSeq = ++sceneRequestSeq
  expandedSceneLoading.value = true
  expandedSceneError.value = ''
  expandedSceneAgents.value = []
  try {
    const response = await v2PortalApi.pageByPortal({
      page: 1,
      size: 200,
      tag: sceneName,
      enabled: true,
    })
    if (requestSeq !== sceneRequestSeq) return
    const records = response.data?.data?.records || []
    expandedSceneAgents.value = records
      .filter((agent) => agent.enabled !== false)
      .sort((a, b) => getUseCount(b) - getUseCount(a))
  } catch (error) {
    if (requestSeq !== sceneRequestSeq) return
    console.error('加载精选场景智能体失败', error)
    expandedSceneError.value = '加载失败，请稍后重试'
  } finally {
    if (requestSeq === sceneRequestSeq) {
      expandedSceneLoading.value = false
    }
  }
}

async function startCreate() {
  if (!accountStore.isLoggedIn) {
    await router.push(RoutePaths.LOGIN)
    return
  }
  if (!creatorAgentId.value) {
    const response = await v2PortalApi.creatorAgentDetail()
    creatorAgentId.value = String(response.data.data.id)
  }
  initialPrompt.value = prompt.value.trim()
  chatKey.value += 1
  drawerOpen.value = true
}

function openMineAgents() {
  ;(window as any).__PORTAL_V2__ = false
  router.push({ name: RouteNames.AGENT })
}

function openCreatorAdmin() {
  router.push({ name: RouteNames.CREATOR_ADMIN })
}

function openCreatorHistory() {
  creatorChatRef.value?.openCreatorHistory()
}

async function handleAccountMenu({ key }: { key: string }) {
  if (key === 'login') {
    await router.push(RoutePaths.LOGIN)
  } else if (key === 'settings') {
    settingsVisible.value = true
  } else if (key === 'logout') {
    Modal.confirm({
      title: '确认退出登录？',
      content: '退出后需要重新登录才能使用我的智能体。',
      onOk: async () => {
        await accountStore.logout()
        await router.push(RoutePaths.LOGIN)
      },
    })
  }
}

async function cloneToMine(agent: AgentDefinitionVO) {
  await runCloneAgentFlow({
    sourceAgentId: agent.id,
    sourceName: agent.name,
    onSuccess: (data) => {
      router.push({ name: RouteNames.AGENT, params: { id: data.id } })
    },
  })
}

function tryAgent(agent: AgentDefinitionVO) {
  const routeData = router.resolve({ name: 'Chat', params: { agentId: String(agent.id) } })
  window.open(routeData.href, '_blank')
}

async function handleAgentAction(agent: AgentDefinitionVO) {
  if (isDefaultTenant.value) {
    tryAgent(agent)
    return
  }
  await cloneToMine(agent)
}

function openScene(scene: Scene, index: number, event: MouseEvent | KeyboardEvent) {
  const target = event.currentTarget as HTMLElement | null
  if (!target || typeof window === 'undefined') return
  if (sceneCloseTimer) {
    clearTimeout(sceneCloseTimer)
    sceneCloseTimer = null
  }

  const rect = target.getBoundingClientRect()
  const finalWidth = Math.min(580, window.innerWidth - 40)
  const finalHeight = Math.min(660, window.innerHeight - 48)
  const finalRect = {
    left: (window.innerWidth - finalWidth) / 2,
    top: (window.innerHeight - finalHeight) / 2,
    width: finalWidth,
    height: finalHeight,
  }

  expandedScene.value = {
    scene,
    index,
    motion: {
      x: rect.left - finalRect.left,
      y: rect.top - finalRect.top,
      scaleX: rect.width / finalRect.width,
      scaleY: rect.height / finalRect.height,
      finalLeft: finalRect.left,
      finalTop: finalRect.top,
      finalWidth: finalRect.width,
      finalHeight: finalRect.height,
    },
  }
  scenePhase.value = 'origin'
  requestAnimationFrame(() => requestAnimationFrame(() => {
    scenePhase.value = 'open'
  }))
  loadExpandedSceneAgents(scene.name)
}

function closeScene() {
  if (!expandedScene.value || scenePhase.value === 'closing') return
  sceneRequestSeq += 1
  scenePhase.value = 'closing'
  sceneCloseTimer = setTimeout(() => {
    expandedScene.value = null
    scenePhase.value = 'origin'
    expandedSceneAgents.value = []
    expandedSceneError.value = ''
    expandedSceneLoading.value = false
  }, 520)
}

function onKeyDown(event: KeyboardEvent) {
  if (event.key === 'Escape') closeScene()
}

onMounted(() => {
  loadTags()
  loadAgents()
  window.addEventListener('keydown', onKeyDown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeyDown)
  if (sceneCloseTimer) {
    clearTimeout(sceneCloseTimer)
  }
})
</script>

<template>
  <div :class="['marketplace-v5', { 'drawer-open': drawerOpen }]">
    <div class="page-surface">
      <header class="topbar">
        <div class="brand">
          <img class="logo" :src="publicAsset('assets/xingtu-logo.png')" alt="星图AI Logo" />
          <b>星图AI智能体</b>
        </div>
        <nav>
          <button class="active" type="button">智能体广场</button>
          <button v-if="isPlatformAdmin" type="button" @click="openCreatorAdmin">
            小图系统配置
          </button>
        </nav>
        <div class="top-actions">
          <label class="search">
            <SearchOutlined />
            <input v-model="search" placeholder="搜索智能体名称或能力" />
          </label>
          <button class="mine" type="button" @click="openMineAgents">
            <AppstoreOutlined />我的智能体
          </button>
          <ADropdown :trigger="['click']" placement="bottomRight">
            <button class="avatar" type="button" :aria-label="userLabel" :title="userLabel">
              {{ userInitial }}
            </button>
            <template #overlay>
              <AMenu :items="accountMenuItems" @click="handleAccountMenu" />
            </template>
          </ADropdown>
        </div>
      </header>

      <main>
        <div class="discovery-stage">
          <video
            class="discovery-video"
            autoplay
            muted
            loop
            playsinline
            :poster="publicAsset('media/flowing-blue-waves-poster.jpg')"
            aria-hidden="true"
          >
            <source :src="publicAsset('media/flowing-blue-waves.mp4')" type="video/mp4" />
          </video>
          <div class="discovery-video-wash" />

          <section class="creator-strip">
            <img class="mascot" :src="publicAsset('assets/xiaotu-blue-v3.png')" alt="小图" />
            <div class="creator-copy">
              <h1>一句话，创建你的专属智能体</h1>
              <p>告诉小图你想让智能体做什么</p>
            </div>
            <div class="prompt-box">
              <input
                v-model="prompt"
                placeholder="例如：创建一个每天整理行业新闻并生成简报的智能体"
                @keydown.enter="startCreate"
              />
              <button type="button" @click="startCreate">
                开始创建<ArrowRightOutlined />
              </button>
            </div>
          </section>

          <section class="featured">
            <div class="section-head">
              <h2>精选场景</h2>
              <div>
                <button type="button" @click="scroller?.scrollBy({ left: -420, behavior: 'smooth' })">
                  <ArrowLeftOutlined />
                </button>
                <button type="button" @click="scroller?.scrollBy({ left: 420, behavior: 'smooth' })">
                  <ArrowRightOutlined />
                </button>
              </div>
            </div>
            <div ref="scroller" class="scene-row">
              <article
                v-for="(scene, index) in scenes"
                :key="scene.name"
                class="scene-card"
                role="button"
                tabindex="0"
                :aria-label="`展开${scene.name}场景`"
                @click="openScene(scene, index, $event)"
                @keydown.enter.prevent="openScene(scene, index, $event)"
                @keydown.space.prevent="openScene(scene, index, $event)"
              >
                <img :src="scene.image" alt="" />
                <div class="scene-content">
                  <h3>{{ scene.name }}</h3>
                  <div v-for="agent in scene.agents" :key="agent.id" class="scene-agent">
                    <img :src="agent.avatar || avatarUrl(String(agent.name))" alt="" />
                    <span>{{ agent.name }}</span>
                  </div>
                  <div v-if="!scene.agents.length" class="scene-agent scene-agent-empty">
                    暂无已发布智能体
                  </div>
                </div>
              </article>
            </div>
          </section>
        </div>

        <section class="market">
          <div class="market-tabs">
            <h2>智能体</h2>
          </div>
          <div class="filters">
            <div class="categories">
              <button
                v-for="item in categories"
                :key="item"
                type="button"
                :class="{ active: category === item }"
                @click="category = item"
              >
                {{ item }}
              </button>
              <button type="button">更多 <DownOutlined /></button>
            </div>
            <div class="sort">
              <button type="button" :class="{ active: sort === 'popular' }" @click="sort = 'popular'">最热</button>
              <button type="button" :class="{ active: sort === 'latest' }" @click="sort = 'latest'">最新</button>
            </div>
          </div>
          <div class="agent-grid">
            <div v-if="loading" class="empty">正在加载智能体…</div>
            <template v-else>
              <article v-for="agent in filteredAgents" :key="agent.id" class="agent-card">
                <div class="agent-title">
                  <img :src="agent.avatar || avatarUrl(String(agent.name), 'f5d0a9,c0aede,b6e3f4')" alt="" />
                  <div>
                    <h3>{{ agent.name }}</h3>
                    <span>{{ agent.tag || '智能体' }}</span>
                  </div>
                </div>
                <p>{{ agent.description || '暂无描述' }}</p>
                <div class="card-foot">
                  <span><TeamOutlined /> {{ getUseCount(agent) }} 人使用</span>
                  <button type="button" @click="handleAgentAction(agent)">
                    {{ isDefaultTenant ? '立即试用' : '复制到我的空间' }}
                    <PlusOutlined v-if="!isDefaultTenant" />
                  </button>
                </div>
              </article>
              <div v-if="!filteredAgents.length" class="empty">没有找到匹配的智能体，换个关键词试试。</div>
            </template>
          </div>
        </section>
      </main>
    </div>

    <template v-if="expandedScene">
      <button
        :class="['scene-backdrop', scenePhase]"
        type="button"
        aria-label="关闭场景详情"
        @click="closeScene"
      />
      <section
        :class="['scene-folder', scenePhase]"
        :style="{
          '--origin-x': `${expandedScene.motion.x}px`,
          '--origin-y': `${expandedScene.motion.y}px`,
          '--origin-scale-x': expandedScene.motion.scaleX,
          '--origin-scale-y': expandedScene.motion.scaleY,
          '--folder-left': `${expandedScene.motion.finalLeft}px`,
          '--folder-top': `${expandedScene.motion.finalTop}px`,
          '--folder-width': `${expandedScene.motion.finalWidth}px`,
          '--folder-height': `${expandedScene.motion.finalHeight}px`,
        }"
        aria-modal="true"
        role="dialog"
        aria-labelledby="scene-folder-title"
      >
        <header class="scene-folder-head">
          <img class="scene-folder-cover" :src="expandedScene.scene.image" alt="" />
          <div class="scene-folder-heading">
            <span>精选场景</span>
            <h2 id="scene-folder-title">{{ expandedScene.scene.name }}</h2>
            <p>为这个工作场景准备的智能体</p>
          </div>
          <button type="button" aria-label="关闭" @click="closeScene">
            <CloseOutlined />
          </button>
        </header>
        <div class="scene-folder-body">
          <div class="scene-folder-label">
            <b>智能体</b>
            <span>{{ expandedSceneLoading ? '加载中' : `${expandedSceneAgents.length} 个` }}</span>
          </div>
          <div class="scene-folder-grid">
            <div v-if="expandedSceneLoading" class="empty">
              正在加载该场景的智能体…
            </div>
            <div v-else-if="expandedSceneError" class="empty">
              {{ expandedSceneError }}
            </div>
            <template v-else>
              <article v-for="agent in expandedSceneAgents" :key="agent.id" class="folder-agent">
                <img :src="agent.avatar || avatarUrl(String(agent.name))" alt="" />
                <div class="folder-agent-content">
                  <span>{{ agent.tag || expandedScene.scene.name }}</span>
                  <h3>{{ agent.name }}</h3>
                  <p>{{ agent.description || '暂无描述' }}</p>
                </div>
                <button class="folder-agent-action" type="button" @click.stop="handleAgentAction(agent)">
                  {{ isDefaultTenant ? '试用' : '复制' }}
                  <PlusOutlined v-if="!isDefaultTenant" />
                </button>
              </article>
              <div v-if="!expandedSceneAgents.length" class="empty">
                该场景暂无已发布智能体
              </div>
            </template>
          </div>
        </div>
      </section>
    </template>

    <aside :class="['drawer', { open: drawerOpen }]" :aria-hidden="!drawerOpen">
      <div class="drawer-head">
        <div>
          <img class="mascot small" :src="publicAsset('assets/xiaotu-blue-v3.png')" alt="" />
          <span>
            <b>小图 · 创建智能体</b>
            <small>用自然语言完成智能体配置</small>
          </span>
        </div>
        <button type="button" class="drawer-history" @click="openCreatorHistory">
          <HistoryOutlined />历史
        </button>
        <button type="button" class="drawer-close" aria-label="关闭" @click="drawerOpen = false">
          <CloseOutlined />
        </button>
      </div>
      <div class="creator-chat">
        <Chat
          v-if="drawerOpen"
          ref="creatorChatRef"
          :key="chatKey"
          :chat-agent-id="creatorAgentId"
          :show-account="false"
          :initial-prompt="initialPrompt"
          welcome-headline="你好，我是小图"
          welcome-description="我会一步步帮你梳理需求。配置只确认一次，之后自动完成创建、部署和验证。"
          :starter-questions-override="[
            '我有一个模糊想法，请一步步帮我梳理',
            '创建一个销售跟进助手，整理客户聊天记录并给出下一步建议',
            '创建一个每天整理行业新闻并生成中文简报的智能体',
          ]"
          portal-agent
          compact
        />
      </div>
    </aside>

    <AModal
      v-model:open="settingsVisible"
      wrap-class-name="full-modal"
      :footer="null"
      :destroy-on-close="true"
      :width="'100%'"
    >
      <SettingsModal />
    </AModal>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/explore/v5' as *;
</style>
