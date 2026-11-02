<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { RouteNames, RoutePaths } from '@/router'
import { useAccountStore } from '@/stores'
import { LogoutOutlined, ExclamationCircleOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import ExamTimerBadge from './ExamTimerBadge.vue'
import { issueSsoTicket } from '@/api/exam'

const props = withDefaults(
  defineProps<{
    /** 当前作品 submissionId，用于「我的作品」「评审状态」跳转；缺省时这两项禁用 */
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
    message.warning('进入智能体平台失败，请稍后重试')
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
    <div class="portal-brand">
      <span class="portal-brand-icon" aria-hidden="true">
        <img src="@/assets/images/logo/logo.png" alt="星图AI" />
      </span>
      <strong class="portal-brand-name">星图AI智能考试平台</strong>
    </div>

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

    <div class="portal-topbar-actions">
      <ExamTimerBadge class="portal-timer" />
      <a-popover
        v-model:open="popoverOpen"
        placement="bottomRight"
        trigger="click"
        :arrow="false"
        overlay-class-name="portal-user-popover"
      >
        <button type="button" class="portal-user" aria-label="账号菜单">
          <a-avatar :size="32" :style="{ background: avatarColor }">{{ avatarText }}</a-avatar>
          <div class="portal-user-meta">
            <span class="portal-user-name">{{ username }}</span>
            <span v-if="roleLabel" class="portal-user-sub">{{ roleLabel }}</span>
          </div>
          <svg class="portal-user-caret" viewBox="0 0 12 12" width="12" height="12" aria-hidden="true">
            <path d="M2 4l4 4 4-4" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </button>

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
    </div>
  </header>
</template>


<style scoped lang="scss">
/* ── 顶栏：默认（桌面）单行 | 手机两行 ── */
.portal-topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  grid-template-areas: 'brand nav actions';
  align-items: center;
  gap: 12px 20px;
  min-height: 68px;
  padding: 0 28px;
  background: #fff;
  border-bottom: 1px solid #dbe6f6;
  box-sizing: border-box;
}

.portal-brand {
  grid-area: brand;
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 0 0 auto;
  min-width: 0;
  max-width: 240px;
  overflow: hidden;
}

.portal-brand-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%);
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.25);
  flex-shrink: 0;

  img {
    width: 28px;
    height: 28px;
    display: block;
    filter: brightness(0) invert(1);
  }
}

.portal-brand-name {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.2;
}

.portal-topbar-actions {
  grid-area: actions;
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 0 0 auto;
}

.portal-nav {
  grid-area: nav;
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  min-height: 40px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}
.portal-nav::-webkit-scrollbar {
  display: none;
}

.portal-nav-btn {
  flex: 0 0 auto;
  height: 40px;
  padding: 0 14px;
  border: 0;
  border-radius: 8px;
  background: none;
  color: #566982;
  font-family: inherit;
  font-size: 14px;
  font-weight: 500;
  line-height: 1;
  white-space: nowrap;
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
  height: 40px;
  padding: 4px 10px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  box-sizing: border-box;
  flex-shrink: 0;
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
  min-width: 0;
  max-width: 140px;
}
.portal-user-name,
.portal-user-sub {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
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

@media (max-width: 1023px) {
  .portal-topbar {
    grid-template-columns: minmax(0, 1fr) auto;
    grid-template-areas: 'brand brand' 'nav actions';
    gap: 8px;
    padding: 10px 12px;
  }
  .portal-brand {
    gap: 8px;
    max-width: none;
  }
  .portal-brand-icon {
    width: 32px;
    height: 32px;

    img {
      width: 24px;
      height: 24px;
    }
  }
  .portal-brand-name {
    font-size: 14px;
  }
  .portal-topbar-actions {
    gap: 6px;
  }
  .portal-user {
    width: 40px;
    padding: 0;
    justify-content: center;
  }
  .portal-user-meta,
  .portal-user-caret {
    display: none;
  }
  .portal-timer {
    flex-shrink: 0;
  }
  .portal-timer :deep(.exam-timer-label) {
    display: none;
  }
  .portal-timer.waiting :deep(.exam-timer-label) {
    display: inline;
  }
  .portal-nav-btn {
    border-radius: 999px;
    background: #f3f7fd;
    font-size: 13px;
    font-weight: 600;
  }
}

@media (max-width: 480px) {
  .portal-topbar {
    padding: 10px;
    column-gap: 6px;
  }
  .portal-nav {
    gap: 4px;
    min-height: 36px;
  }
  .portal-nav-btn {
    height: 36px;
    padding: 0 6px;
    font-size: 12px;
  }
  .portal-topbar-actions {
    gap: 4px;
  }
  .portal-user {
    width: 36px;
    height: 36px;
  }
  .portal-timer {
    padding: 0 8px;
  }
}

</style>
