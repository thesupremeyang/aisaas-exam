<!--
  v2 门户主布局

  左侧 Sider（Logo + 菜单 + 用户区）+ 右侧（顶部 sub-header + 内容区）
  移动端：Sider 转为 Drawer

  @author huxuehao
-->
<!-- eslint-disable vue/multi-word-component-names -->
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAccountStore } from '@/stores'
import { isPortalV3, isPortalV4, isPortalV5 } from '@/config/portal'
import { AppLogoV2, AppMenuV2, AppHeaderV2, ToolbarV2 } from '@/components/layout-v2'

const route = useRoute()
const router = useRouter()
const accountStore = useAccountStore()
const { getRefresh } = useAccountStore()
const isRefresh = computed(() => getRefresh())

/** 退出"查看考生配置"模拟态：恢复管理员令牌 → 等路由完成 → 整页刷新（确保页面数据与令牌一致） */
function exitAdminView() {
  accountStore.exitTenantView()
  router
    .push('/exam/admin/submissions')
    .catch(() => undefined)
    .finally(() => {
      window.setTimeout(() => window.location.reload(), 100)
    })
}

/* ===================== 响应式：移动端抽屉 ===================== */
const isMobile = ref(false)
const drawerOpen = ref(false)
const isExplorePage = computed(() => route.path === '/explore')
const isFullWidthExplore = computed(() => (isPortalV3 || isPortalV4 || isPortalV5) && isExplorePage.value)
const showDesktopSider = computed(() => !isMobile.value && !isFullWidthExplore.value)

const MOBILE_BREAKPOINT = 768

function syncBreakpoint() {
  // SSR 安全：仅在浏览器执行
  if (typeof window === 'undefined') return
  isMobile.value = window.innerWidth <= MOBILE_BREAKPOINT
}

onMounted(() => {
  syncBreakpoint()
  window.addEventListener('resize', syncBreakpoint)
})

onBeforeUnmount(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('resize', syncBreakpoint)
  }
})

/** 打开移动端抽屉 */
function openMobileMenu() {
  drawerOpen.value = true
}
</script>

<template>
  <div class="app-layout-v2">
    <!-- 桌面端左侧 Sider -->
    <aside
      v-if="showDesktopSider"
      :class="['layout-v2-sider', { 'layout-v3-sider': isPortalV3 }]"
    >
      <AppLogoV2 class="sider-v2-logo" :collapsed="false" />
      <AppMenuV2 class="sider-v2-menu" :collapsed="false" />
      <ToolbarV2 class="sider-v2-toolbar" />
    </aside>

    <!-- 移动端抽屉 -->
    <ADrawer
      v-if="isMobile"
      v-model:open="drawerOpen"
      placement="left"
      :width="280"
      :closable="false"
      :header-style="{ display: 'none' }"
      :body-style="{ padding: 0, background: 'rgba(255,255,255,0.98)', height: '100%' }"
      class="layout-v2-drawer"
    >
      <div class="layout-v2-drawer-panel">
        <AppLogoV2 class="sider-v2-logo" :collapsed="false" />
        <AppMenuV2 class="sider-v2-menu" :collapsed="false" />
        <ToolbarV2 class="sider-v2-toolbar" />
      </div>
    </ADrawer>

    <!-- 右侧主区 -->
    <div class="layout-v2-main">
      <AppHeaderV2
        v-if="!((isPortalV4 || isPortalV5) && isExplorePage)"
        class="layout-v2-header"
        :show-mobile-trigger="isMobile && !isExplorePage"
        :show-brand="isFullWidthExplore || isMobile"
        :show-utilities="false"
        @open-mobile-menu="openMobileMenu"
      />

      <!-- 管理员"查看考生配置"模拟态：全局横幅（任何页面可见，防迷失） -->
      <a-alert
        v-if="accountStore.isTenantViewActive"
        type="warning"
        show-icon
        class="admin-view-global-banner"
      >
        <template #message>
          <span class="admin-view-text">
            正在以管理员身份查看考生「{{ accountStore.adminViewCandidate }}」的租户数据——
            当前所有页面的智能体 / Skill / 知识库等内容均为该考生租户数据，并非全平台数据。
          </span>
          <a class="admin-view-exit" @click="exitAdminView">退出并返回答卷管理</a>
        </template>
      </a-alert>

      <main class="layout-v2-content">
        <!-- 背景装饰层 -->
        <div class="layout-v2-bg-deco" aria-hidden="true">
          <div class="deco-v2-orb deco-v2-orb-1"></div>
          <div class="deco-v2-orb deco-v2-orb-2"></div>
        </div>

        <router-view
          v-slot="{ Component }"
          :key="route.path + isRefresh"
        >
          <transition name="slide-right" mode="out-in" appear>
            <div style="height: 100%; position: relative;">
              <component :is="Component" />
            </div>
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/layout-v2/layout-v2' as *;

.admin-view-global-banner {
  margin: 0 16px 8px;
  border-radius: 8px;

  .admin-view-text {
    font-weight: 500;
  }

  .admin-view-exit {
    margin-left: 12px;
    font-weight: 600;
    white-space: nowrap;
  }
}
</style>
