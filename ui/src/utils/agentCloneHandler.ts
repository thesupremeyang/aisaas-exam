/**
 * 广场克隆 agent — 路由 + 全局挂载（方案 G）
 *
 * <p>设计：
 * <ul>
 *   <li>提供 installCloneRoutes(router)：动态注入 /agent-clone-demo 路由，
 *       渲染独立测试页 AgentCloneTestPage</li>
 *   <li>提供 openCloneModal({ sourceAgentId, name })：在任何组件里通过
 *       window.__openAgentCloneModal(...) 唤起 modal</li>
 * </ul>
 * </p>
 *
 * <p>这些函数全部依赖新增的文件，不修改任何现有前端文件。</p>
 *
 * @author agent-clone-feature
 */
import type { Router } from 'vue-router'

/**
 * 注入克隆相关路由。
 * 建议在 App.vue 的 setup 里 app.use(router) 之后调用一次。
 */
export function installCloneRoutes(router: Router) {
  // /agent-clone-demo：独立测试页（不修改 Explore/index.vue）
  router.addRoute({
    path: '/agent-clone-demo',
    name: 'AgentCloneDemo',
    component: () => import('@/views/Explore/components/AgentCloneTestPage.vue'),
    meta: { title: '克隆测试', hidden: true },
  })
}

/**
 * 全局唤起克隆确认弹窗。
 * 由 AgentCloneTestPage 注入；调用方应确保该页面已挂载（用户进入 /agent-clone-demo）。
 */
export function openCloneModal(opts: { sourceAgentId: string | number; name?: string }) {
  const fn = (window as any).__openAgentCloneModal as
    | ((p: { sourceAgentId: string | number; name?: string }) => void)
    | undefined
  if (typeof fn === 'function') {
    fn(opts)
  } else {
    // 兜底：跳到测试页，把参数带在 query 上
    window.location.hash = `#/agent-clone-demo?sourceAgentId=${opts.sourceAgentId}${
      opts.name ? `&name=${encodeURIComponent(opts.name)}` : ''
    }`
  }
}
