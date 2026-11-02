<!--
  考试门户独立全屏布局

  不渲染左侧 Sider / 顶部 Header，门户以整页形式呈现。
  门户内的"提交作品 / 制作智能体"等跳转会离开本布局、回到业务布局（v2 Sider）。

  @author huxuehao
-->
<!-- eslint-disable vue/multi-word-component-names -->
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { RouteNames } from '@/router'
import PortalTopbar from '@/views/exam/PortalTopbar.vue'

const route = useRoute()
const router = useRouter()
const submissionId = computed(() => String(route.params.submissionId || sessionStorage.getItem('exam:submissionId') || ''))

function requestSubmission() {
  router.push({ name: RouteNames.EXAM_PORTAL, query: { action: 'submission' } })
}

function requestAssessment() {
  router.push({ name: RouteNames.EXAM_PORTAL, query: { action: 'assessment' } })
}
</script>

<template>
  <div class="exam-portal-layout">
    <PortalTopbar
      :submission-id="submissionId"
      @request-submission="requestSubmission"
      @request-assessment="requestAssessment"
    />
    <router-view v-slot="{ Component }" :key="route.path">
      <component :is="Component" />
    </router-view>
  </div>
</template>

<style scoped lang="scss">
.exam-portal-layout {
  min-height: 100vh;
  width: 100%;
  overflow-x: hidden;
  background: #f5f7fb;
}
</style>
