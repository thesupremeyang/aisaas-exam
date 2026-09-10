<!--
  v2 门户主布局

  左侧 Sider（Logo + 菜单 + 用户区）+ 右侧（顶部 sub-header + 内容区）
  移动端：Sider 转为 Drawer

  @author huxuehao
-->
<!-- eslint-disable vue/multi-word-component-names -->
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useAccountStore } from '@/stores'
import { isPortalV3, isPortalV4, isPortalV5 } from '@/config/portal'
import { AppLogoV2, AppMenuV2, AppHeaderV2, ToolbarV2 } from '@/components/layout-v2'

const route = useRoute()
const { getRefresh } = useAccountStore()
const isRefresh = computed(() => getRefresh())

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
      :body-style="{ padding: 0, background: 'rgba(255,255,255,0.98)' }"
      class="layout-v2-drawer"
    >
      <AppLogoV2 class="sider-v2-logo" :collapsed="false" />
      <AppMenuV2 class="sider-v2-menu" :collapsed="false" />
      <ToolbarV2 class="sider-v2-toolbar" />
    </ADrawer>

    <!-- 右侧主区 -->
    <div class="layout-v2-main">
      <AppHeaderV2
        v-if="!((isPortalV4 || isPortalV5) && isExplorePage)"
        class="layout-v2-header"
        :show-mobile-trigger="isMobile && !isExplorePage"
        :show-brand="isFullWidthExplore"
        :show-utilities="false"
        @open-mobile-menu="openMobileMenu"
      />

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
</style>
