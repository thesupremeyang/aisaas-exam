<script setup lang="ts">
/**
 * 存储管理聚合组件 — 聚合存储配置、文件管理、文件日志
 *
 * @author huxuehao
 */
import { ref } from 'vue'
import StorageConfig from './StorageConfig.vue'
import FileManager from './FileManager.vue'
import FileLog from './FileLog.vue'

/** 当前激活的Tab */
const activeTab = ref('config')
</script>

<template>
  <div>
    <h2 class="settings-page-title">存储管理</h2>

    <ATabs v-model:activeKey="activeTab">
      <ATabPane key="config" tab="存储配置">
        <StorageConfig />
      </ATabPane>
      <ATabPane key="files" tab="文件管理">
        <FileManager />
      </ATabPane>
      <ATabPane key="logs" tab="文件日志">
        <FileLog />
      </ATabPane>
    </ATabs>
  </div>
</template>
<style lang="scss" scoped>
@use '@/styles/modules/_settings.scss' as *;
</style>
