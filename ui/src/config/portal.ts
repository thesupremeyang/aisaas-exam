/**
 * 门户样式配置
 *
 * 通过环境变量 VITE_UI_PORTAL 控制：
 *   - v1：顶部水平菜单（Header + Content + Footer）
 *   - v2：左侧 Sider + 右侧 sub-header + Content
 *   - v3：保留 v2 布局，启用新版智能体广场与可折叠专属空间
 *   - v4：启用小图对话式创建智能体广场
 *   - v5：启用原型版智能体广场并接入后端数据
 *
 * 修改后需重启 dev 服务才能生效。
 *
 * @author huxuehao
 */

export type PortalVariant = 'v1' | 'v2' | 'v3' | 'v4' | 'v5'

/** 当前门户样式版本 */
export const PORTAL_VARIANT: PortalVariant =
  (import.meta.env.VITE_UI_PORTAL as PortalVariant) || 'v1'

/** 是否启用新版门户样式：只要不是 v1，就进入智能体广场 */
export const isPortalV2 = PORTAL_VARIANT !== 'v1'

/** 是否启用 v3 门户样式 */
export const isPortalV3 = PORTAL_VARIANT === 'v3'

/** 是否启用 v4 智能体广场 */
export const isPortalV4 = PORTAL_VARIANT === 'v4'

/** 是否启用 v5 原型版智能体广场 */
export const isPortalV5 = PORTAL_VARIANT === 'v5'

/** 是否启用侧边栏门户布局 */
export const isSidePortal = isPortalV2 || isPortalV3 || isPortalV4 || isPortalV5

/** 是否启用 v1 门户样式 */
export const isPortalV1 = PORTAL_VARIANT === 'v1'
