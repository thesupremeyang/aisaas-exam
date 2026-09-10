<!--
  v2 门户用户区组件

  位置：侧栏底部
  内容：刷新、文档、用户下拉（设置/退出）

  @author huxuehao
-->
<script setup lang="ts">
import { computed, h, ref } from 'vue'
import { useRouter } from 'vue-router'
import { RoutePaths } from '@/router/constants'
import { useAccountStore } from '@/stores'
import {
  UndoOutlined,
  LogoutOutlined,
  SettingOutlined,
  BookOutlined,
} from '@ant-design/icons-vue'
import { Modal } from 'ant-design-vue'
import SettingsModal from '@/components/settings/SettingsModal.vue'

const router = useRouter()
const { logout, setRefresh, userInfo } = useAccountStore()

const settingsVisible = ref(false)

/** 用户名 */
const username = computed(() => userInfo?.username || '用户')

/** 头像文字（首字母） */
const avatarText = computed(() => {
  if (!userInfo?.username) return '?'
  return userInfo.username.charAt(0).toUpperCase()
})

/** 角色标签 */
const roleLabel = computed(() => {
  const roles = (userInfo as any)?.roles || (userInfo as any)?.role || ''
  const roleStr = Array.isArray(roles) ? roles.join(',') : String(roles)
  if (roleStr.includes('PLATFORM_ADMIN')) return '管理员'
  if (roleStr.includes('TENANT_OWNER')) return '租户管理员'
  if (roleStr.includes('ADMIN')) return '管理'
  return ''
})

/** 根据用户名生成稳定的头像背景色 */
const avatarColor = computed(() => {
  const name = userInfo?.username || ''
  const colors = [
    '#2563eb', '#7c3aed', '#059669', '#dc2626',
    '#d97706', '#0891b2', '#4f46e5', '#be185d',
  ]
  let hash = 0
  for (let i = 0; i < name.length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash)
  }
  return colors[Math.abs(hash) % colors.length]
})

/** 下拉菜单 */
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

function openMarkdownDoc() {
  window.open('/doc.html#/', '_blank')
}
</script>

<template>
  <div class="user-section-v2 sider-v2-user-inner">
    <ATooltip placement="right">
      <template #title><span>刷新</span></template>
      <AButton class="icon-v2-action" shape="circle" type="text" @click="setRefresh">
        <template #icon><UndoOutlined /></template>
      </AButton>
    </ATooltip>

    <ATooltip placement="right">
      <template #title><span>使用手册</span></template>
      <AButton class="icon-v2-action" shape="circle" type="text" @click="openMarkdownDoc">
        <template #icon><BookOutlined /></template>
      </AButton>
    </ATooltip>

    <ADropdown :trigger="['hover']" placement="topRight">
      <div class="user-v2-info">
        <AAvatar :size="32" :style="{ background: avatarColor }">
          {{ avatarText }}
        </AAvatar>
        <div class="user-v2-meta">
          <span class="user-v2-name">{{ username }}</span>
          <span v-if="roleLabel" class="user-v2-role">{{ roleLabel }}</span>
        </div>
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
</template>

<style scoped lang="scss">
.user-section-v2 {
  padding: 10px 14px;
  display: flex;
  align-items: center;
  gap: 4px;
  border-top: 1px solid #e2e8f0;

  .icon-v2-action {
    border: none;
    color: #64748b;
    width: 30px;
    height: 30px;
    font-size: 13px;
    border-radius: 6px;

    &:hover {
      color: #2563eb;
      background: #f1f5f9;
    }
  }

  .user-v2-info {
    cursor: pointer;
    margin-left: auto;
    padding: 4px 6px 4px 8px;
    border-radius: 8px;
    transition: background-color 0.2s ease;
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;

    &:hover {
      background-color: #f3f4f6;
    }

    .user-v2-meta {
      display: flex;
      flex-direction: column;
      gap: 1px;
      min-width: 0;
    }

    .user-v2-name {
      font-size: 13px;
      font-weight: 600;
      color: #1e293b;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      max-width: 100px;
      line-height: 1.3;
    }

    .user-v2-role {
      font-size: 11px;
      color: #94a3b8;
      line-height: 1.2;
    }
  }

  /* 中等屏幕收起为窄条时，仅显示头像 */
  @media (max-width: 1024px) {
    padding: 10px 8px;
    flex-direction: column;
    gap: 6px;

    .icon-v2-action {
      width: 36px;
      height: 36px;
    }

    .user-v2-info {
      margin-left: 0;
      padding: 0;
      justify-content: center;

      .user-v2-meta {
        display: none;
      }
    }
  }
}
</style>
