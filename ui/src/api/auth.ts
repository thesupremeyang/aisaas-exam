import request from '@/utils/request'
import setting from "@/config/setting";
import type { ApiResponse } from '@/types'
import type {
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  ChangePasswordRequest,
  UpdateProfileRequest,
  SelectTenantRequest
} from '@/types'

/**
 * 用户登录
 * POST /auth/login
 */
export function login(data: LoginRequest) {
  return request.post<ApiResponse<LoginResponse>>('/api/auth/login', data)
}

/**
 * 用户注册
 * POST /auth/register
 */
export function register(data: RegisterRequest) {
  return request.post<ApiResponse<boolean>>('/api/auth/register', data)
}

/**
 * 刷新Token
 * POST /auth/refresh-token
 */
export function refreshToken(refreshToken: string) {
  return request.post<ApiResponse<LoginResponse>>('/api/auth/refresh-token', { refreshToken }, {
    headers: {
      [setting.refreshTokenRequest]: true
    }
  })
}

/**
 * 用户退出
 * POST /auth/logout
 */
export function logout() {
  return request.post<ApiResponse<void>>('/api/auth/logout')
}

/**
 * 刷新当前页面的在线状态
 */
export function heartbeat() {
  return request.post<ApiResponse<void>>('/api/auth/heartbeat', null, {
    silent: true,
    retry: 1,
    timeout: 15000,
  })
}

/**
 * 修改密码
 * POST /auth/change-password
 */
export function changePassword(data: ChangePasswordRequest) {
  return request.post<ApiResponse<boolean>>('/api/auth/change-password', data)
}

/**
 * 修改个人信息
 * POST /auth/update-profile
 */
export function updateProfile(data: UpdateProfileRequest) {
  return request.post<ApiResponse<boolean>>('/api/auth/update-profile', data)
}

/**
 * 管理员新增用户
 * POST /auth/admin/create-account
 */
export function adminCreateAccount(data: RegisterRequest) {
  return request.post<ApiResponse<boolean>>('/api/auth/admin/create-account', data)
}

/**
 * 选择/切换租户（登录后选择当前使用的租户，也用于登录后的主动切换）
 * POST /auth/select-tenant
 */
export function selectTenant(data: SelectTenantRequest) {
  return request.post<ApiResponse<LoginResponse>>('/api/auth/select-tenant', data)
}

/**
 * 平台管理员进入考生租户查看（模拟令牌：考生租户上下文、无平台豁免；
 * 退出查看时前端换回管理员原令牌）
 * POST /auth/admin/view-tenant
 */
export function adminViewTenant(tenantId: string | number) {
  return request.post<ApiResponse<LoginResponse>>('/api/auth/admin/view-tenant', { tenantId })
}

/**
 * 通过ChatKey换取Token
 * POST /auth/chat-key-token/{chatKey}
 */
export function chatKeyToken(chatKey: String) {
  return request.post<ApiResponse<LoginResponse>>(`/api/auth/chat-key-token/${chatKey}`)
}
