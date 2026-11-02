<!--
  v2 门户菜单组件（竖向分组侧边栏）

  分组：我的专属空间：智能体管理 / 模型 / 提示词 / 工具 / Hook / Skill / MCP / 知识库 / 敏感词

  @author huxuehao
-->
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { isPortalV3 } from '@/config/portal'
import { useAccountStore } from '@/stores'
import { isPlatformAdminUsername } from '@/utils/platformAdmin'
import {
  RobotOutlined,
  ApiOutlined,
  FileTextOutlined,
  ToolOutlined,
  LoginOutlined,
  AppstoreOutlined,
  CloudServerOutlined,
  DatabaseOutlined,
  SafetyCertificateOutlined,
  TrophyOutlined,
  AuditOutlined,
  CalendarOutlined,
  TeamOutlined,
  UnorderedListOutlined,
  FileSearchOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import type { Component } from 'vue'

withDefaults(defineProps<{ collapsed?: boolean }>(), {
  collapsed: false,
})

const route = useRoute()
const router = useRouter()

/* ===================== 菜单定义 ===================== */

/** 业务菜单定义（含分组、图标、配色） */
interface MenuDef {
  path: string
  label: string
  icon: Component
  group: 'workspace' | 'system'
  color: string
}

const bizMenuDefs: MenuDef[] = [
  // 考试门户
  { path: 'exam/portal', label: '提交作品', icon: TrophyOutlined, group: 'workspace', color: '#2b5cff' },
  // 我的专属空间
  { path: 'agent', label: '智能体管理', icon: RobotOutlined, group: 'workspace', color: '#8b5cf6' },
  { path: 'model', label: '大模型管理', icon: ApiOutlined, group: 'workspace', color: '#3b82f6' },
  { path: 'prompt', label: '提示词管理', icon: FileTextOutlined, group: 'workspace', color: '#10b981' },
  { path: 'skill', label: 'Skill管理', icon: AppstoreOutlined, group: 'workspace', color: '#06b6d4' },
  { path: 'mcp', label: 'MCP管理', icon: CloudServerOutlined, group: 'workspace', color: '#6366f1' },
  { path: 'knowledge', label: '知识库管理', icon: DatabaseOutlined, group: 'workspace', color: '#14b8a6' },
   { path: 'tool', label: '工具管理', icon: ToolOutlined, group: 'workspace', color: '#f97316' },
  { path: 'hook', label: '钩子管理', icon: LoginOutlined, group: 'workspace', color: '#ec4899' },
  { path: 'sensitive', label: '敏感词管理', icon: SafetyCertificateOutlined, group: 'workspace', color: '#ef4444' },
]

const accountStore = useAccountStore()
const isPlatformAdmin = computed(
  () => isPlatformAdminUsername(accountStore.userInfo?.username),
)
/**
 * 管理员"查看考生配置"模拟态：侧栏切换为考生端菜单
 * （显示智能体管理/Skill/知识库等"我的专属空间"，隐藏考试管理组），
 * 与考生当时的界面一致；退出模拟态后恢复管理员侧栏。
 */
const isTenantView = computed(() => accountStore.isTenantViewActive)
const systemItems = computed<MenuDef[]>(() => (isPlatformAdmin.value && !isTenantView.value)
  ? [
      { path: 'exam/admin/campaigns', label: '考试场次', icon: CalendarOutlined, group: 'system', color: '#0ea5e9' },
      { path: 'exam/admin/questions', label: '试题管理', icon: FileTextOutlined, group: 'system', color: '#d97706' },
      { path: 'exam/admin/candidates', label: '考生管理', icon: TeamOutlined, group: 'system', color: '#16a34a' },
      { path: 'exam/admin/submissions', label: '答卷管理', icon: UnorderedListOutlined, group: 'system', color: '#7c3aed' },
      { path: 'exam/admin/grading', label: 'AI 阅卷', icon: FileSearchOutlined, group: 'system', color: '#0d9488' },
      { path: 'reviewer-scores', label: '成绩与发布', icon: AuditOutlined, group: 'system', color: '#f59e0b' },
      { path: 'account', label: '用户管理', icon: UserOutlined, group: 'system', color: '#6366f1' },
    ]
  : [])

/**
 * 我的专属空间分组菜单。
 * 平台管理员正常登录时隐藏（侧栏只保留"考试管理"组）；
 * 模拟态（查看考生配置）下恢复显示，与考生当时的界面一致。
 */
const workspaceItems = computed<MenuDef[]>(() => (isPlatformAdmin.value && !isTenantView.value)
  ? []
  : bizMenuDefs.filter((d) =>
    d.group === 'workspace' && !(isPlatformAdmin.value && d.path === 'exam/portal'),
  ))

/** 当前激活的菜单 key（取去斜杠后的完整路径，支持多级路径精确高亮） */
const selectedKey = computed(() => route.path.replace(/^\/+/, '') || '')

/** 点击菜单项跳转 */
function onSelect(path: string) {
  router.push('/' + path)
}

/** 菜单项是否命中当前路由（含子路径） */
function isActive(item: MenuDef): boolean {
  return selectedKey.value === item.path || selectedKey.value.startsWith(item.path + '/')
}
</script>

<template>
  <nav class="app-menu-v2">
    <!-- v1/v2 原有导航 -->
    <template v-if="!isPortalV3">
      <div v-if="workspaceItems.length" class="menu-v2-group">
      <div
        v-for="item in workspaceItems"
        :key="item.path"
        :class="['menu-v2-item', { active: selectedKey === item.path }]"
        @click="onSelect(item.path)"
      >
        <component
          :is="item.icon"
          class="menu-v2-icon"
          :style="{ color: selectedKey === item.path ? '#2563eb' : item.color }"
        />
        <span class="menu-v2-label">{{ item.label }}</span>
      </div>
      </div>

      <div v-if="systemItems.length" class="menu-v2-group">
      <div class="group-v2-title">考试管理</div>
      <div
        v-for="item in systemItems"
        :key="item.path"
        :class="['menu-v2-item', { active: isActive(item) }]"
        @click="onSelect(item.path)"
      >
        <component
          :is="item.icon"
          class="menu-v2-icon"
          :style="{ color: selectedKey === item.path ? '#2563eb' : item.color }"
        />
        <span class="menu-v2-label">{{ item.label }}</span>
      </div>
      </div>
    </template>

    <!-- v3：整根侧栏专属于我的专属空间 -->
    <div v-else-if="!collapsed" class="workspace-v3-drawer-menu">
      <button
        v-for="item in workspaceItems"
        :key="item.path"
        type="button"
        :class="['workspace-v3-drawer-item', { active: selectedKey === item.path }]"
        @click="onSelect(item.path)"
      >
        <component :is="item.icon" :style="{ color: item.color }" />
        <span>{{ item.label }}</span>
      </button>
      <template v-if="systemItems.length">
        <div class="workspace-v3-drawer-title">考试管理</div>
        <button
          v-for="item in systemItems"
          :key="item.path"
          type="button"
          :class="['workspace-v3-drawer-item', { active: isActive(item) }]"
          @click="onSelect(item.path)"
        >
          <component :is="item.icon" :style="{ color: item.color }" />
          <span>{{ item.label }}</span>
        </button>
      </template>
    </div>
  </nav>
</template>

<style scoped lang="scss">
@use '@/styles/layout-v2/menu-v2' as *;
</style>
