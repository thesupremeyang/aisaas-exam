/**
 * 公共页面路由
 *
 * @author huxuehao
 */

import type { AppRouteRecordRaw } from '../types'
import { RouteNames, RoutePaths } from '@/router'
import { isSidePortal } from '@/config/portal'
import { useAccountStore } from '@/stores'
import { isPlatformAdminUsername } from '@/utils/platformAdmin'

/**
 * 公共页面路由配置
 *
 * 根路径按身份动态分流：
 * - 平台管理员（非"查看考生配置"模拟态）→ 考试场次管理页（管理员端功能）
 * - 模拟态管理员 / 考生 / 其余版本 → 考试门户（exam/portal）或智能体管理（/agent）
 */
const commonRoutes: AppRouteRecordRaw[] = [
  {
    path: RoutePaths.ROOT,
    // 注意：RoutePaths 常量不带前导斜杠，redirect 必须显式补 "/"，
    // 否则 vue-router 按相对路径解析，从非根页面访问 "/" 会拼出错误路径
    redirect: () => {
      try {
        const accountStore = useAccountStore()
        if (accountStore.isLoggedIn
            && isPlatformAdminUsername(accountStore.userInfo?.username)
            && !accountStore.isTenantViewActive) {
          return `/${RoutePaths.EXAM_ADMIN}`
        }
      } catch {
        // pinia 未就绪（极早导航）时按默认分流
      }
      return isSidePortal ? `/${RoutePaths.EXAM_PORTAL}` : `/${RoutePaths.AGENT}`
    },
  },
  {
    path: `${RoutePaths.CHAT}/:agentId`,
    name: RouteNames.CHAT,
    component: () => import('@/views/Chat/index.vue'),
    meta: {
      title: '对话',
      hidden: true,
    },
  },
  {
    path: `${RoutePaths.CHAT_HISTORY}/:agentId`,
    name: RouteNames.CHAT_HISTORY,
    component: () => import('@/views/ChatHistory/index.vue'),
    meta: {
      title: '对话历史',
      hidden: true,
    },
  },
  {
    path: `${RoutePaths.COMMUNICATION}/:chatKey`,
    name: RouteNames.COMMUNICATION,
    component: () => import('@/views/Communication/index.vue'),
    meta: {
      title: '对话',
      hidden: true,
    },
  },
  {
    path: RoutePaths.PREVIEW,
    name: RouteNames.PREVIEW,
    component: () => import('@/pages/AntdPreview.vue'),
    meta: {
      title: 'AntdPreview',
      hidden: true,
    },
  },
  // 文档页面已迁移至 doc 子应用（doc.html）
  {
    path: RoutePaths.NOT_FOUND,
    name: RouteNames.NOT_FOUND,
    component: () => import('@/pages/NotFound.vue'),
    meta: {
      title: '页面不存在',
      hidden: true,
    },
  }
]

export default commonRoutes
