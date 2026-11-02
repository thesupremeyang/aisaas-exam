<!--
  v2 门户顶部 sub-header 组件

  左侧：移动端 Drawer 触发按钮 + 全局搜索框
  右侧：工具按钮 + 头像

  @author huxuehao
-->
<script setup lang="ts">
import { computed, h, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAccountStore } from '@/stores'
import { BookOutlined, MenuOutlined, LogoutOutlined, SettingOutlined, UndoOutlined } from '@ant-design/icons-vue'
import { Modal } from 'ant-design-vue'
import { RoutePaths } from '@/router/constants'
import SettingsModal from '@/components/settings/SettingsModal.vue'
import ExamTimerBadge from '@/views/exam/ExamTimerBadge.vue'

const router = useRouter()
const { logout, userInfo, setRefresh } = useAccountStore()

const settingsVisible = ref(false)

const props = defineProps<{
  showMobileTrigger?: boolean
  showBrand?: boolean
  showUtilities?: boolean
}>()

const emit = defineEmits<{
  (e: 'open-mobile-menu'): void
}>()

const avatarText = computed(() => {
  if (!userInfo?.username) return '?'
  return userInfo.username.charAt(0).toUpperCase()
})

const avatarColor = computed(() => {
  const name = userInfo?.username || ''
  const colors = ['#2563eb', '#7c3aed', '#059669', '#dc2626', '#d97706', '#0891b2', '#4f46e5', '#be185d']
  let hash = 0
  for (let i = 0; i < name.length; i++) hash = name.charCodeAt(i) + ((hash << 5) - hash)
  return colors[Math.abs(hash) % colors.length]
})

const menuItems = [
  { key: 'profile', label: '系统设置', icon: () => h(SettingOutlined) },
  { type: 'divider' },
  { key: 'logout', danger: true, label: '退出登录', icon: () => h(LogoutOutlined) },
]

async function handleMenuClick({ key }: { key: string }) {
  if (key === 'logout') {
    Modal.confirm({
      title: '确认',
      icon: null,
      content: '确认退出当前系统,是否继续?',
      onOk: async () => {
        await logout()
        await router.push(RoutePaths.LOGIN)
      },
    })
  } else if (key === 'profile') {
    settingsVisible.value = true
  }
}

function onOpenMobile() {
  emit('open-mobile-menu')
}

function openMarkdownDoc() {
  window.open('/doc.html#/', '_blank')
}
</script>

<template>
  <div class="app-header-v2 flex items-center justify-between">
    <!-- 左侧：移动端菜单触发 -->
    <div class="header-v2-left flex items-center">
      <button
        v-if="props.showMobileTrigger"
        class="mobile-trigger-v2"
        type="button"
        @click="onOpenMobile"
      >
        <MenuOutlined />
      </button>
      <div v-if="props.showBrand" class="header-v3-brand">
        <span class="header-v3-logo"><img src="@/assets/images/logo/logo.png" alt="星图 AI Logo"></span>
        <strong>星图AI智能考试平台</strong>
      </div>
    </div>

    <!-- 右侧操作 -->
    <div class="actions-v2 flex items-center">
      <ExamTimerBadge />
      <template v-if="props.showUtilities">
        <ATooltip placement="bottom">
          <template #title><span>刷新</span></template>
          <button class="icon-v2-btn" type="button" @click="setRefresh">
            <UndoOutlined />
          </button>
        </ATooltip>
        <ATooltip placement="bottom">
          <template #title><span>使用手册</span></template>
          <button class="icon-v2-btn" type="button" @click="openMarkdownDoc">
            <BookOutlined />
          </button>
        </ATooltip>
      </template>

      <ADropdown :trigger="['hover']" placement="bottomRight">
        <div class="header-user-v2">
          <AAvatar :size="30" :style="{ background: avatarColor, cursor: 'pointer' }">
            {{ avatarText }}
          </AAvatar>
        </div>
        <template #overlay>
          <AMenu :items="menuItems" @click="handleMenuClick" />
        </template>
      </ADropdown>

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
  </div>
</template>

<style scoped lang="scss">
.app-header-v2 {
  width: 100%;
  height: 100%;
  padding: 0 28px;
}

.header-v2-left {
  flex: 1;
  min-width: 0;
}

.header-v3-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;

  strong {
    color: #17233b;
    font-size: 16px;
    letter-spacing: 0.2px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

.header-v3-logo {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%);
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.22);
  flex-shrink: 0;

  img {
    width: 28px;
    height: 28px;
    filter: brightness(0) invert(1);
  }
}

@media (max-width: 768px) {
  .app-header-v2 {
    padding: 0 12px;
    gap: 8px;
  }

  .header-v2-left {
    min-width: 0;
    flex: 1 1 auto;
    overflow: hidden;
  }

  .mobile-trigger-v2 {
    margin-right: 6px;
    flex-shrink: 0;
  }

  .header-v3-brand {
    gap: 8px;
    min-width: 0;
  }

  .header-v3-logo {
    width: 32px;
    height: 32px;

    img {
      width: 24px;
      height: 24px;
    }
  }

  .header-v3-brand strong {
    font-size: 14px;
  }

  .actions-v2 {
    gap: 8px;
  }
}

.mobile-trigger-v2 {
  display: none;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  font-size: 18px;
  margin-right: 12px;
  transition: all 0.2s;

  &:hover {
    background: #f1f5f9;
    color: #2563eb;
  }

  @media (max-width: 768px) {
    display: inline-flex;
  }
}

.actions-v2 {
  gap: 14px;
  flex-shrink: 0;
}

.icon-v2-btn {
  width: 38px;
  height: 38px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  display: grid;
  place-items: center;
  font-size: 17px;
  transition: all 0.2s ease;

  &:hover {
    background: #f1f5f9;
    color: #2563eb;
  }
}

.header-user-v2 {
  cursor: pointer;
  padding: 2px;
  border-radius: 50%;
  transition: all 0.2s ease;

  &:hover {
    background: #f1f5f9;
  }
}
</style>
