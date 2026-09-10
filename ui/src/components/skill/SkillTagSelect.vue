/**
 * 技能包标签选择器
 *
 * 支持选择已有标签，也支持在下拉面板中新增标签。
 */
<script setup lang="ts">
import { computed, defineComponent, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'

const props = withDefaults(defineProps<{
  modelValue: string
  categories?: string[]
  placeholder?: string
  maxLength?: number
}>(), {
  categories: () => [],
  placeholder: '选择或输入标签',
  maxLength: 6,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const categoryOptions = ref<string[]>([])
const categorySearchText = ref('')
const newCategoryInput = ref('')

const selectedValue = computed({
  get: () => props.modelValue || '',
  set: (value: string) => emit('update:modelValue', value || ''),
})

const filteredCategories = computed(() => {
  if (!categorySearchText.value) {
    return categoryOptions.value
  }
  const searchLower = categorySearchText.value.toLowerCase()
  const filtered = categoryOptions.value.filter(category =>
    category.toLowerCase().includes(searchLower),
  )
  if (!filtered.includes(categorySearchText.value)) {
    filtered.unshift(categorySearchText.value)
  }
  return filtered
})

watch(() => props.categories, (categories) => {
  const current = selectedValue.value.trim()
  categoryOptions.value = Array.from(new Set([
    ...categories,
    ...(current ? [current] : []),
  ]))
}, { immediate: true, deep: true })

function handleCategorySearch(value: string) {
  categorySearchText.value = value
}

function addCategoryItem(event?: Event) {
  event?.preventDefault()
  const value = newCategoryInput.value.trim()
  if (!value) return
  if (value.length > props.maxLength) {
    message.warning(`标签长度不能超过${props.maxLength}个字符`)
    return
  }
  if (!categoryOptions.value.includes(value)) {
    categoryOptions.value.push(value)
  }
  selectedValue.value = value
  newCategoryInput.value = ''
  categorySearchText.value = ''
}

const VNodes = defineComponent({
  props: {
    vnodes: {
      type: Object,
      required: true,
    },
  },
  render() {
    return this.vnodes
  },
})
</script>

<template>
  <ASelect
    v-model:value="selectedValue"
    style="width: 100%"
    :placeholder="placeholder"
    show-search
    :filter-option="false"
    @search="handleCategorySearch"
  >
    <ASelectOption v-for="category in filteredCategories" :key="category" :value="category">
      {{ category }}
    </ASelectOption>
    <template #dropdownRender="{ menuNode: menu }">
      <VNodes :vnodes="menu" />
      <ADivider style="margin: 4px 0" />
      <div class="skill-tag-add-row" @mousedown.stop>
        <AInput
          v-model:value="newCategoryInput"
          class="skill-tag-add-input"
          placeholder="输入新标签"
          :maxlength="maxLength"
          @pressEnter="addCategoryItem"
        />
        <AButton type="primary" size="small" @click="addCategoryItem">
          <template #icon>
            <PlusOutlined />
          </template>
          添加
        </AButton>
      </div>
    </template>
  </ASelect>
</template>

<style scoped lang="scss">
.skill-tag-add-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px;
  box-sizing: border-box;
}

.skill-tag-add-input {
  flex: 1;
  min-width: 0;
}
</style>
