<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { RouteNames, RoutePaths } from '@/router'
import { useAccountStore } from '@/stores'
import { LogoutOutlined, ExclamationCircleOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import AppLogoV2 from '@/components/layout-v2/AppLogoV2.vue'
import ExamTimerBadge from './ExamTimerBadge.vue'
import { issueSsoTicket } from '@/api/exam'

const props = withDefaults(
  defineProps<{
    /** 当前作品 submissionId，用于「我的作品」「AI 智评」跳转；缺省时这两项禁用 */
    submissionId?: string
  }>(),
  { submissionId: '' },
)

const router = useRouter()
const emit = defineEmits<{
  requestSubmission: []
  requestAssessment: []
}>()

const accountStore = useAccountStore()
const { userInfo, logout } = accountStore

const appBasePath = ((import.meta.env.BASE_URL as string) || '/').replace(/\/?$/, '/')

interface NavItem {
  label: string
  name: string
  to?: { name: string; params?: Record<string, string> }
  needSubmission?: boolean
  external?: string
}

type ExamWindowState = 'WAITING' | 'OPEN' | 'ENDED'
const examWindowState = ref<ExamWindowState | null>(null)
const scoreQueryEnabled = ref(false)

function syncExamWindowState(): void {
  const state = sessionStorage.getItem('exam:window-state')
  examWindowState.value = state === 'WAITING' || state === 'OPEN' || state === 'ENDED' ? state : null
  scoreQueryEnabled.value = sessionStorage.getItem('exam:score-query-mode') === '1'
}

const navItems = computed<NavItem[]>(() => {
  if (examWindowState.value === 'ENDED' && scoreQueryEnabled.value) {
    return [
      { label: '考试成绩', name: RouteNames.EXAM_PORTAL, to: { name: RouteNames.EXAM_PORTAL }, needSubmission: false },
      { label: '智能体管理', name: 'AGENT_MANAGEMENT', external: window.location.origin + appBasePath + '#/agent' },
    ]
  }
  if (examWindowState.value !== 'OPEN') return []
  return [
    { label: '提交作品', name: RouteNames.EXAM_PORTAL, to: { name: RouteNames.EXAM_PORTAL }, needSubmission: false },
    { label: '制作智能体', name: 'AGENT_MANAGEMENT', external: window.location.origin + appBasePath + '#/agent' },
  ]
})

async function openAgentManagement(): Promise<void> {
  const campaignId = sessionStorage.getItem('exam:campaignId') || ''
  try {
    if (!campaignId) throw new Error('no campaign')
    const response = await issueSsoTicket(campaignId, '/#/exam/portal')
    const ticket = response.data.data?.ticket
    if (!ticket) throw new Error('no ticket')
    await router.push({ path: '/agent', query: { examTicket: ticket } })
  } catch {
    message.error('进入智能体平台失败，请稍后重试')
  }
}

function navigate(item: NavItem): void {
  if (item.external) {
    if (item.name === 'AGENT_MANAGEMENT') {
      void openAgentManagement()
      return
    }
    window.open(item.external, '_blank', 'noopener')
    return
  }
  const submissionId = props.submissionId || sessionStorage.getItem('exam:submissionId') || ''
  if (item.needSubmission && !submissionId) {
    if (item.name === RouteNames.CANDIDATE_SUBMISSION) emit('requestSubmission')
    else emit('requestAssessment')
    return
  }
  if (item.needSubmission) {
    router.push({ name: item.name, params: { submissionId } })
    return
  }
  if (!item.to) return
  router.push(item.to)
}

function isActive(item: NavItem): boolean {
  const path = router.currentRoute.value.path
  if (item.name === RouteNames.EXAM_PORTAL) return path.startsWith('/exam/portal')
  if (item.name === RouteNames.CANDIDATE_ASSESSMENT)
    return path.includes('/assessment') || router.currentRoute.value.name === RouteNames.CANDIDATE_ASSESSMENT
  if (item.name === 'AGENT_MANAGEMENT') {
    return path.startsWith('/agent') || path.startsWith('/knowledge') || path.startsWith('/skill')
  }
  return path.startsWith('/exam/submission')
}

onMounted(() => {
  syncExamWindowState()
  window.addEventListener('exam:context-updated', syncExamWindowState)
  window.addEventListener('exam:window-state-updated', syncExamWindowState)
})

onBeforeUnmount(() => {
  window.removeEventListener('exam:context-updated', syncExamWindowState)
  window.removeEventListener('exam:window-state-updated', syncExamWindowState)
})

/** ===== 右侧用户区 ===== */
const popoverOpen = ref(false)
const username = computed(() => userInfo?.nickname || userInfo?.username || '用户')
const roleLabel = computed(() => {
  if (userInfo?.username) return `考生账号 · ${userInfo.username}`
  const map: Record<string, string> = {
    TENANT_OWNER: '租户拥有者',
    TENANT_ADMIN: '租户管理员',
    TENANT_EDITOR: '编辑者',
    TENANT_VIEWER: '查看者',
  }
  return (userInfo?.tenantRole && map[userInfo.tenantRole]) || ''
})
const avatarText = computed(() => (userInfo?.username ? userInfo.username.charAt(0).toUpperCase() : '?'))
const avatarColor = computed(() => {
  const name = userInfo?.username || ''
  const colors = ['#2563eb', '#7c3aed', '#059669', '#dc2626', '#d97706', '#0891b2', '#4f46e5', '#be185d']
  let hash = 0
  for (let i = 0; i < name.length; i++) hash = name.charCodeAt(i) + ((hash << 5) - hash)
  return colors[Math.abs(hash) % colors.length]
})

async function confirmLogout(): Promise<void> {
  popoverOpen.value = false
  Modal.confirm({
    title: '退出登录',
    icon: h(ExclamationCircleOutlined, { style: { color: '#faad14' } }),
    content: `确认要退出账号「${username.value}」吗？`,
    okText: '退出登录',
    okButtonProps: { danger: true },
    cancelText: '取消',
    centered: true,
    onOk: async () => {
      await logout()
      await router.push(RoutePaths.LOGIN)
    },
  })
}
</script>

<template>
  <header class="portal-topbar">
    <AppLogoV2 class="portal-brand-logo" />
    <nav class="portal-nav">
      <button
        v-for="item in navItems"
        :key="item.name"
        class="portal-nav-btn"
        :class="{ active: isActive(item) }"
        @click="navigate(item)"
      >
        {{ item.label }}
      </button>
    </nav>

    <ExamTimerBadge class="portal-timer" />

    <a-popover
      v-model:open="popoverOpen"
      placement="bottomRight"
      trigger="click"
      :arrow="false"
      overlay-class-name="portal-user-popover"
    >
      <div class="portal-user">
        <a-avatar :size="34" :style="{ background: avatarColor }">{{ avatarText }}</a-avatar>
        <div class="portal-user-meta">
          <span class="portal-user-name">{{ username }}</span>
          <span v-if="roleLabel" class="portal-user-sub">{{ roleLabel }}</span>
        </div>
        <svg class="portal-user-caret" viewBox="0 0 12 12" width="12" height="12">
          <path d="M2 4l4 4 4-4" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </div>

      <template #content>
        <div class="user-card">
          <div class="user-card-head">
            <a-avatar :size="46" :style="{ background: avatarColor }">{{ avatarText }}</a-avatar>
            <div class="user-card-info">
              <div class="user-card-name">{{ username }}</div>
              <div v-if="roleLabel" class="user-card-role">{{ roleLabel }}</div>
            </div>
          </div>
          <a-divider style="margin: 12px 0" />
          <button class="user-card-logout" @click="confirmLogout">
            <logout-outlined />
            <span>退出登录</span>
          </button>
        </div>
      </template>
    </a-popover>
  </header>
</template>


<style scoped lang="scss">
.portal-topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 30px;
  height: 68px;
  padding: 0 28px;
  background: #fff;
  border-bottom: 1px solid #dbe6f6;
}
/* 复用 agent 页面的 AppLogoV2，覆盖外层适配顶栏 */
.portal-brand-logo {
  width: auto;
  height: 100%;
  padding: 0;
  gap: 10px;
}
.portal-nav {
  display: flex;
  gap: 8px;
  flex: 1;
}
.portal-nav-btn {
  padding: 8px 14px;
  border: 0;
  border-radius: 8px;
  background: none;
  color: #566982;
  font: 14px inherit;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.portal-nav-btn:hover:not(:disabled) {
  color: #1266e8;
  background: #eaf3ff;
}
.portal-nav-btn.active {
  color: #1266e8;
  background: #eaf3ff;
  font-weight: 600;
}
.portal-nav-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* 右侧用户区 */
.portal-user {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px;
  border: 1px solid transparent;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.18s, border-color 0.18s, box-shadow 0.18s;
}
.portal-user:hover {
  background: #f3f7fd;
  border-color: #dbe6f6;
}
.portal-user-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}
.portal-user-name {
  font-size: 14px;
  font-weight: 600;
  color: #0a1e45;
}
.portal-user-sub {
  font-size: 11px;
  color: #8a98ad;
}
.portal-user-caret {
  color: #9aa7ba;
  transition: transform 0.18s;
}
.portal-user:hover .portal-user-caret {
  color: #1266e8;
}

/* 用户卡片弹层 */
.user-card {
  width: 240px;
}
.user-card-head {
  display: flex;
  align-items: center;
  gap: 12px;
}
.user-card-info {
  min-width: 0;
}
.user-card-name {
  font-size: 15px;
  font-weight: 600;
  color: #0a1e45;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.user-card-role {
  display: inline-block;
  margin-top: 4px;
  padding: 1px 8px;
  border-radius: 999px;
  background: #eaf3ff;
  color: #1266e8;
  font-size: 12px;
}
.user-card-tenant {
  margin-top: 4px;
  font-size: 12px;
  color: #8a98ad;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.user-card-logout {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding: 9px 0;
  border: 1px solid #ffccc7;
  border-radius: 8px;
  background: #fff;
  color: #d4380d;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.18s, border-color 0.18s, color 0.18s;
}
.user-card-logout:hover {
  background: #fff1f0;
  border-color: #ff7875;
  color: #cf1322;
}
.user-card-logout:active {
  background: #ffe7e3;
}

@media (max-width: 640px) {
  .portal-topbar {
    gap: 14px;
    padding: 0 16px;
  }
  .portal-nav {
    gap: 0;
    overflow-x: auto;
  }
  .portal-nav-btn {
    padding: 8px 10px;
    white-space: nowrap;
  }
  .portal-user-meta {
    display: none;
  }
}
</style>
