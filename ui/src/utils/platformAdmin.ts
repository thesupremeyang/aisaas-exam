const platformAdminUsernames = new Set(
  String(import.meta.env.VITE_PLATFORM_ADMIN_USERNAMES || 'admin')
    .split(',')
    .map((value) => value.trim().toLowerCase())
    .filter(Boolean),
)

/**
 * 登录后把账号表标记的平台管理员用户名注册进集合。
 * 平台管理员判定已改为数据库标记驱动（account.platform_admin），
 * 环境变量 VITE_PLATFORM_ADMIN_USERNAMES 仅作构建期兜底。
 */
export function registerPlatformAdminUsername(username?: string | null): void {
  if (username && username.trim()) {
    platformAdminUsernames.add(username.trim().toLowerCase())
  }
}

export function isPlatformAdminUsername(username?: string | null): boolean {
  return !!username && platformAdminUsernames.has(username.trim().toLowerCase())
}
