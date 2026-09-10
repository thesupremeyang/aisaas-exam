<!--
  v2 门户 Logo 组件
  @author huxuehao
-->
<script setup lang="ts">
interface Props {
  /** 项目名称 */
  projectName?: string
  collapsed?: boolean
}

withDefaults(defineProps<Props>(), {
  projectName: '星图AI智能考试平台',
  collapsed: false,
})
</script>

<template>
  <div :class="['app-logo-v2', 'sider-v2-logo-inner', 'flex', 'items-center', { collapsed }]">
    <div class="logo-v2-icon-wrap flex items-center justify-center">
      <img src="@/assets/images/logo/logo.png" alt="logo" width="32" height="32">
    </div>
    <span class="logo-v2-project-name">{{ projectName }}</span>
  </div>
</template>

<style scoped lang="scss">
.app-logo-v2 {
  width: 100%;
  height: 100%;
  padding: 0 20px;
  gap: 10px;
  overflow: hidden;

  .logo-v2-icon-wrap {
    width: 36px;
    height: 36px;
    border-radius: 10px;
    background: linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%);
    flex-shrink: 0;
    box-shadow: 0 4px 12px rgba(59, 130, 246, 0.25);

    img {
      width: 28px;
      height: 28px;
      filter: brightness(0) invert(1);
    }
  }

  .logo-v2-project-name {
    font-size: 16px;
    font-weight: 700;
    color: #0f172a;
    white-space: nowrap;
    background: linear-gradient(135deg, #1e293b 0%, #475569 100%);
    -webkit-background-clip: text;
    background-clip: text;
    -webkit-text-fill-color: transparent;
    letter-spacing: 0.3px;
  }

  &.collapsed {
    padding: 0;
    justify-content: center;

    .logo-v2-project-name {
      display: none;
    }
  }

  /* 中等屏幕收起时只显示图标 */
  @media (max-width: 1024px) {
    padding: 0;
    justify-content: center;

    .logo-v2-project-name {
      display: none;
    }
  }

  /* 移动端 Drawer 内恢复正常显示 */
  @media (max-width: 768px) {
    padding: 0 20px;
    justify-content: flex-start;

    .logo-v2-project-name {
      display: inline;
    }
  }
}
</style>
