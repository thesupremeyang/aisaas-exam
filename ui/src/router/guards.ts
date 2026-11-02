/**
 * 路由守卫
 *
 * @author huxuehao
 */

import type { Router } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { useAccountStore } from '@/stores'
import { isPlatformAdminUsername } from '@/utils/platformAdmin'
import { RoutePaths, WHITE_LIST } from './constants'

// 配置NProgress
NProgress.configure({
  showSpinner: false,
  trickleSpeed: 200,
  minimum: 0.3,
})

/**
 * 设置页面标题
 */
export function setPageTitle(title?: string): void {
  const defaultTitle = '星图AI智能考试系统'
  if (title) {
    document.title = `${title} - ${defaultTitle}`
  } else {
    document.title = defaultTitle
  }
}

/**
 * 设置路由守卫
 */
export function setupRouterGuard(router: Router): void {
  // 全局前置守卫
  router.beforeEach(async (to, from, next) => {
    // 开启进度条
    NProgress.start()

    // 设置页面标题
    setPageTitle(to.meta?.title as string)

    // 放行“外置对话链接”
    if (to.path.startsWith('/communication/')) {
      next()
      NProgress.done()
      return
    }

    const accountStore = useAccountStore()
    const hasToken = accountStore.isLoggedIn

    // 已登录
    if (hasToken) {
      if (to.path === '/login') {
        // 已登录且访问登录页，重定向到首页
        next({ path: '/' })
        NProgress.done()
      } else {
        // 检查是否已获取用户信息
        if (!accountStore.userInfo) {
          try {
            // 获取用户信息
            await accountStore.fetchCurrentUserInfo()
          } catch (error) {
            // 获取用户信息失败，清除token并重定向到登录页
            await accountStore.logout()
            next({ path: '/login' })
            NProgress.done()
            return
          }
        }

        // 平台管理员不进入考生考试门户，避免被要求报名；改落考试场次管理页
        // （侧栏已对平台管理员隐藏"我的专属空间"，不能再落 /agent，
        //   否则登录后会停留在一个菜单上没有入口的智能体管理页面）
        // "查看考生配置"模拟态例外：模拟态本身就需要停留在 /agent。
        const isPlatformAdmin = isPlatformAdminUsername(accountStore.userInfo?.username)
        if (isPlatformAdmin && !accountStore.isTenantViewActive) {
          if (to.path === '/exam/portal') {
            next({ path: `/${RoutePaths.EXAM_ADMIN}` })
            NProgress.done()
            return
          }
          // 兜底：管理员手动访问 /agent（如历史收藏/回退）时回到管理员端
          if (to.path === `/${RoutePaths.AGENT}`) {
            next({ path: `/${RoutePaths.EXAM_ADMIN}` })
            NProgress.done()
            return
          }
        }

        next()
      }
    } else {
      // 未登录
      if (WHITE_LIST.some((path) => to.path === path)) {
        // 在白名单中，直接访问
        next()
      } else {
        // 不在白名单中，重定向到登录页
        next({ path: '/login' })
        NProgress.done()
      }
    }
  })

  // 全局后置守卫
  router.afterEach((to, from, failure) => {
    // 关闭进度条
    NProgress.done()

    // 滚动到顶部
    if (!failure) {
      window.scrollTo(0, 0)
    }
  })

  // 全局错误处理
  router.onError((error) => {
    console.error('路由错误:', error)
    NProgress.done()
  })
}

/**
 * 权限检查
 */
export function hasPermission(roles?: string[], userRoles?: string[]): boolean {
  if (!roles || roles.length === 0) {
    return true
  }
  if (!userRoles || userRoles.length === 0) {
    return false
  }
  return roles.some((role) => userRoles.includes(role))
}
