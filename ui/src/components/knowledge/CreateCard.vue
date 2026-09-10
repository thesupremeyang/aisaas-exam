/**
* 新增知识库配置卡片组件
*
* @author huxuehao
*/
<script setup lang="ts">
import { computed } from 'vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import type { KbType } from '@/types'

/**
 * Props定义
 */
const props = defineProps<{
  kbType: KbType | null
}>()

/**
 * Emits定义
 */
const emit = defineEmits<{
  create: [kbType: KbType]
}>()

/**
 * 是否显示类型选择
 */
const showTypeSelect = computed(() => props.kbType === null)

/**
 * 类型选项配置
 */
const typeOptions = [
  {
    value: 'LOCAL',
    label: '本地知识库',
  }
]

/**
 * 当前类型显示文本
 */
const currentTypeText = computed(() => {
  const option = typeOptions.find(opt => opt.value === props.kbType)
  return option?.label.replace('知识库', '') || '知识库'
})

/**
 * 处理创建
 */
function handleCreate(kbType?: KbType) {
  if (kbType) {
    emit('create', kbType)
  } else if (props.kbType) {
    emit('create', props.kbType)
  }
}
</script>

<template>
  <!-- 类型选择态：展示知识库类型选择项 -->
  <div v-if="showTypeSelect" class="create-card type-select">
    <div class="type-list">
      <div
        v-for="option in typeOptions"
        :key="option.value"
        class="type-item"
        @click="handleCreate(option.value as KbType)"
      >
        <div class="type-label">
          <PlusOutlined class="type-plus" />
          <span>{{ option.label }}</span>
        </div>
      </div>
    </div>
  </div>

  <!-- 已选类型态：展示创建入口 -->
  <div
    v-else
    class="create-card flex-col flex-center cursor-pointer"
    @click="handleCreate()"
  >
    <PlusOutlined class="create-icon" />
    <div class="create-text">添加{{ currentTypeText }}</div>
    <div class="create-desc">点击创建新的知识库配置</div>
  </div>
</template>

<style scoped lang="scss">
.create-card {
  display: flex;
  min-height: 180px;
  padding: var(--spacing-xl);
  background-color: var(--color-bg-white);
  border: 2px dotted var(--color-border-base);
  border-radius: var(--border-radius-lg);
  transition: border-color 160ms ease-out, background-color 160ms ease-out, box-shadow 160ms ease-out;
  box-sizing: border-box;
  &:hover {
    border-color: var(--color-primary);
  }

  // 非类型选择态：垂直居中布局
  &.flex-col {
    flex-direction: column;
  }

  &.flex-center {
    align-items: center;
    justify-content: center;
  }

  &:not(.type-select):hover {
    border-color: var(--color-primary);
    background-color: var(--color-bg-light);

    .create-icon {
      color: var(--color-primary);
      transform: scale(1.1);
    }
  }

  // 类型选择态独立控制
  &.type-select {
    padding: var(--spacing-sm);
    align-items: stretch;

    .type-list {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: var(--spacing-sm);
      width: 100%;
      align-content: stretch;

      .type-item {
        display: flex;
        align-items: center;
        justify-content: center;
        padding: var(--spacing-md) var(--spacing-sm);
        min-height: 0;
        border-radius: var(--border-radius-base);
        border: 1px solid var(--color-border-extra-light);
        cursor: pointer;
        transition: transform 160ms ease-out, border-color 160ms ease-out, background-color 160ms ease-out, box-shadow 160ms ease-out;
        background: linear-gradient(135deg, #f8fbff 0%, var(--color-bg-light) 100%);

        @media (hover: hover) and (pointer: fine) {
          &:hover {
            border-color: rgba(15, 116, 255, 0.32);
            background: linear-gradient(135deg, #f0f6ff 0%, #ffffff 100%);
            box-shadow: 0 6px 16px rgba(15, 116, 255, 0.08);
            transform: translateY(-1px);
          }
        }

        &:active {
          transform: scale(0.98);
        }

        .type-label {
          display: flex;
          align-items: center;
          gap: var(--spacing-sm);
          font-size: var(--font-size-base);
          font-weight: 500;
          color: var(--color-text-primary);
          white-space: nowrap;
          text-align: center;

          .type-plus {
            flex-shrink: 0;
            color: var(--color-primary);
            font-size: var(--font-size-lg);
          }
        }
      }
    }
  }

  // 非类型选择态内容样式
  .create-icon {
    font-size: 32px;
    color: var(--color-text-secondary);
    margin-bottom: var(--spacing-md);
    transition: color 160ms ease-out, transform 160ms ease-out;
  }

  .create-text {
    font-size: var(--font-size-base);
    font-weight: 500;
    color: var(--color-text-secondary);
    margin-bottom: var(--spacing-xs);
  }

  .create-desc {
    font-size: var(--font-size-sm);
    color: var(--color-text-placeholder);
    text-align: center;
  }
}

// 响应式布局
@media (max-width: 768px) {
  .create-card.type-select {
    .type-list {
      grid-template-columns: 1fr;
    }
  }
}
</style>
