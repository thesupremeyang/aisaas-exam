import request from '@/utils/request'
import type { ApiResponse } from '@/types'
import type { AccountDTO, AccountVO } from '@/types'
import type { Account } from '@/types'

/**
 * 查询账号列表
 * GET /account/list
 */
export function list(query: AccountDTO) {
  return request.get<ApiResponse<AccountVO[]>>('/api/account/list', { params: query })
}

/**
 * 账号详情
 * GET /account/{id}
 */
export function detail(id: string) {
  return request.get<ApiResponse<AccountVO>>(`/api/account/${id}`)
}

/**
 * 新增账号
 * POST /account
 */
export function save(entity: Account) {
  return request.post<ApiResponse<boolean>>('/api/account', entity)
}

/**
 * 删除账号
 * DELETE /account
 */
export function remove(ids: string[]) {
  return request.delete<ApiResponse<boolean>>('/api/account', { data: ids })
}

/**
 * 禁用/激活用户
 * PUT /account/{id}/toggle-enabled
 */
export function toggleEnabled(id: string, enabled: boolean) {
  return request.put<ApiResponse<boolean>>(`/api/account/${id}/toggle-enabled`, null, {
    params: { enabled }
  })
}

/**
 * 管理员修改用户密码
 * PUT /account/{id}/change-password
 */
export function adminChangePassword(id: string, newPassword: string) {
  return request.put<ApiResponse<boolean>>(`/api/account/${id}/change-password`, null, {
    params: { newPassword }
  })
}

/* ===================== 用户管理（仅平台管理员） ===================== */

/**
 * 平台账号列表（全部账号，含平台管理员标记）
 * GET /account/platform-admin/list
 */
export function listPlatformAccounts(keyword?: string) {
  return request.get<ApiResponse<AccountVO[]>>('/api/account/platform-admin/list', {
    params: keyword ? { keyword } : undefined
  })
}

/**
 * 创建账号（不强制邮箱与租户归属，可直接授予平台管理员身份）
 * POST /account/platform-admin
 */
export function createPlatformAccount(data: {
  username: string
  nickname: string
  password: string
  email?: string
  platformAdmin: boolean
}) {
  return request.post<ApiResponse<boolean>>('/api/account/platform-admin', data)
}

/**
 * 禁用/启用账号
 * PUT /account/platform-admin/{id}/toggle-enabled
 */
export function togglePlatformAccountEnabled(id: string | number, enabled: boolean) {
  return request.put<ApiResponse<boolean>>(
    `/api/account/platform-admin/${id}/toggle-enabled`,
    null,
    { params: { enabled } }
  )
}

/**
 * 重置密码（传明文，服务端以账号 ID 为盐加密存储）
 * PUT /account/platform-admin/{id}/reset-password
 */
export function resetPlatformAccountPassword(id: string | number, newPassword: string) {
  return request.put<ApiResponse<boolean>>(
    `/api/account/platform-admin/${id}/reset-password`,
    null,
    { params: { newPassword } }
  )
}

/**
 * 设置/取消平台管理员身份
 * PUT /account/platform-admin/{id}/platform-admin
 */
export function setPlatformAdminFlag(id: string | number, flag: boolean) {
  return request.put<ApiResponse<boolean>>(
    `/api/account/platform-admin/${id}/platform-admin`,
    null,
    { params: { flag } }
  )
}

/**
 * 删除账号
 * DELETE /account/platform-admin
 */
export function removePlatformAccounts(ids: (string | number)[]) {
  return request.delete<ApiResponse<boolean>>('/api/account/platform-admin', { data: ids })
}
