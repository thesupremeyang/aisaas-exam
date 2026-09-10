<template>
  <div class="vep-renderer">
    <!-- 流式 JSON 不完整 -> 骨架屏 -->
    <VEPSkeleton v-if="!parsed && isStreaming" />

    <!-- 非流式 JSON 无效 -> 降级显示 -->
    <div v-else-if="!parsed" class="vep-fallback">
      <div class="vep-fallback-hint">[VEP 解析失败]</div>
      <pre class="vep-fallback-code">{{ truncatedCode }}</pre>
    </div>

    <!-- vision 为空数组 -> 提示 -->
    <div v-else-if="!parsed.vision" class="vep-fallback">
      <div class="vep-fallback-hint">[VEP 视觉内容为空]</div>
    </div>

    <!-- 逐组件渲染 -->
    <template v-else>
      <CardRenderer v-if="parsed.vision.type === 'card'" :item="parsed.vision" />
      <ChartRenderer v-else-if="parsed.vision.type === 'chart'" :item="parsed.vision" />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { VEPMessage } from './types'
import { parseVEPJson } from '@/utils/chat/vep'
import VEPSkeleton from './VEPSkeleton.vue'
import CardRenderer from './CardRenderer.vue'
import ChartRenderer from './ChartRenderer.vue'

const props = defineProps<{
  /** vep 代码块原始内容（不含 ``` 标记） */
  code: string
  /** 是否处于流式输出阶段 */
  isStreaming?: boolean
}>()

const parsed = computed<VEPMessage | null>(() => parseVEPJson(props.code))

const truncatedCode = computed(() => {
  if (props.code.length <= 300) return props.code
  return props.code.slice(0, 300) + '...'
})
</script>

<style scoped>
.vep-renderer {
  margin: 4px 0;
}

.vep-fallback {
  margin: 8px 0;
  border: 1px solid #ffe58f;
  border-radius: 8px;
  padding: 10px 14px;
  background: #fffbe6;
}

.vep-fallback-hint {
  font-size: 12px;
  color: #d48806;
  margin-bottom: 6px;
}

.vep-fallback-code {
  font-size: 11px;
  color: #8c8c8c;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 120px;
  overflow-y: auto;
  margin: 0;
  line-height: 1.5;
}
</style>
