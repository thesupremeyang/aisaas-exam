/**
 * 账号Store
 *
 * @author huxuehao
 */

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { AccountVO, TenantRole, TenantInfo, SelectTenantRequest, PendingApprovalInfo } from '@/types'
import * as authApi from '@/api/auth'
import * as accountApi from '@/api/account'
import type {
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  ChangePasswordRequest,
  UpdateProfileRequest,
} from '@/types'
import {
  getToken,
  setToken,
  removeToken,
  getRefreshToken,
  setRefreshToken,
  removeRefreshToken,
  getUser,
  setUser,
  removeUser,
} from '@/utils/auth'
import cache from '@/utils/cache'
import setting from '@/config/setting'

/**
 * 账号Store，管理用户登录状态、信息及相关操作
 */
export const useAccountStore = defineStore('account', () => {
  // ========== State ==========
  /** 当前用户信息 */
  const userInfo = ref<AccountVO | null>(getUser())

  /** 访问令牌 */
  const accessToken = ref<string>(getToken())

  /** 刷新令牌 */
  const refreshToken = ref<string>(getRefreshToken())

  /** 登录状态 */
  const isLoggedIn = computed(() => !!accessToken.value && !!userInfo.value)

  // ========== 租户 State ==========

  /** 租户持久化缓存键 */
  const TENANT_KEY = setting.systemName + '-current-tenant'
  const TENANTS_KEY = setting.systemName + '-available-tenants'

  /** 当前租户信息 */
  const currentTenant = ref<TenantInfo | null>(cache.local.getJSON(TENANT_KEY, {}) || null)

  /** 可切换的租户列表 */
  const availableTenants = ref<TenantInfo[]>(cache.local.getJSON(TENANTS_KEY, []))

  /** 是否需要选择租户 */
  const needSelectTenant = ref(false)

  /** 租户角色 */
  const tenantRole = computed(() => currentTenant.value?.role || null)

  // ========== 通用 State ==========

  /** 租户角色等级映射 */
  const tenantRoleLevel: Record<string, number> = {
    TENANT_OWNER: 3,
    TENANT_ADMIN: 2,
    TENANT_EDITOR: 1,
    TENANT_VIEWER: 0
  }

  /** 是否为管理员（租户角色 >= TENANT_ADMIN） */
  const isAdmin = computed(() => {
    const role = tenantRole.value
    if (!role) return false
    return (tenantRoleLevel[role] || 0) >= 2
  })

  /** 是否具有编辑权限（租户角色 >= TENANT_EDITOR） */
  const canEdit = computed(() => {
    const role = tenantRole.value
    if (!role) return false
    return (tenantRoleLevel[role] || 0) >= 1
  })

  /** 是否仅具有只读权限 */
  const isReadOnly = computed(() => tenantRole.value === 'TENANT_VIEWER')

  /** 是否刷新页面 */
  const isRefresh = ref<number>(0)

  // ========== Actions ==========

  function setRefresh() {
    isRefresh.value = new Date().getMilliseconds();
  }

  function getRefresh() {
    return isRefresh.value;
  }

  /**
   * 持久化当前租户信息
   */
  function persistTenant(tenant: TenantInfo | null) {
    currentTenant.value = tenant
    if (tenant) {
      cache.local.setJSON(TENANT_KEY, tenant)
    } else {
      cache.local.remove(TENANT_KEY)
    }
  }

  /**
   * 持久化可用租户列表
   */
  function persistAvailableTenants(tenants: TenantInfo[]) {
    availableTenants.value = tenants
    cache.local.setJSON(TENANTS_KEY, tenants)
  }

  /**
   * 从 LoginResponse 的 UserDetail 中提取租户信息
   */
  function extractTenantFromUserDetail(userDetail: LoginResponse['userDetail']) {
    if (!userDetail) return
    if (userDetail.tenants && userDetail.tenants.length > 0) {
      persistAvailableTenants(userDetail.tenants)
    }
    if (userDetail.tenantId) {
      persistTenant({
        tenantId: userDetail.tenantId,
        tenantCode: userDetail.tenantCode || '',
        tenantName: userDetail.tenantName || userDetail.tenantCode || '',
        role: userDetail.tenantRole || ''
      })
    }
  }

  /**
   * 刷新访问凭证
   * @param data
   */
  function setAccessInfo(data: LoginResponse) {
    // 保存token
    accessToken.value = data.accessToken
    refreshToken.value = data.refreshToken
    setToken({
      value: data.accessToken,
      ttl: data.accessTokenTTL
    })
    setRefreshToken({
      value: data.refreshToken,
      ttl: data.refreshTokenTTL
    })
    // 提取租户信息
    extractTenantFromUserDetail(data.userDetail)
  }

  /**
   * 刷新登录
   * @param data
   */
  async function refreshLogin(data: LoginResponse) {
    setAccessInfo(data)

    // 如果需要完整的用户信息，可以调用详情接口
    if (data.userDetail?.id) {
      await fetchCurrentUserInfo(data.userDetail.id)
    }
  }

  /**
   * 用户登录
   * 返回 needSelectTenant 状态供调用方判断是否需要弹出租户选择
   * @param loginData 登录信息
   */
  async function login(loginData: LoginRequest): Promise<{
    needSelectTenant: boolean
    tenants: TenantInfo[]
    blocked: boolean
    pendingApprovals: PendingApprovalInfo[]
  }> {
    try {
      const response = await authApi.login(loginData)
      const data = response.data.data
      const userDetail = data.userDetail

      // 登录被阻断（有待审批申请）
      if (data.blocked) {
        return {
          needSelectTenant: false,
          tenants: [],
          blocked: true,
          pendingApprovals: data.pendingApprovals || [],
        }
      }

      // 提取并持久化租户信息
      extractTenantFromUserDetail(userDetail)

      // 判断是否需要选择租户
      const needSelect = data.needSelectTenant === true
      const tenants = data.tenants || []

      if (needSelect) {
        // 需要选择租户：不保存 token，由 Login.vue 回调重新登录
        return { needSelectTenant: true, tenants, blocked: false, pendingApprovals: [] }
      }

      // 无需选择租户：直接完成登录
      needSelectTenant.value = false
      await refreshLogin(data)
      return { needSelectTenant: false, tenants, blocked: false, pendingApprovals: [] }
    } catch (error) {
      throw error
    }
  }

  /**
   * 选择租户（登录后选择当前使用的租户）
   * @param data 选择租户请求
   */
  async function selectTenant(data: SelectTenantRequest): Promise<void> {
    try {
      // 标记当前标签页已触发切换，避免 WS 通知导致重复刷新
      sessionStorage.setItem('tenant-change-skip', '1')
      const response = await authApi.selectTenant(data)
      const loginData = response.data.data
      needSelectTenant.value = false
      // 同步可选租户列表
      if (loginData.tenants && loginData.tenants.length > 0) {
        persistAvailableTenants(loginData.tenants)
      }
      await refreshLogin(loginData)
    } catch (error) {
      sessionStorage.removeItem('tenant-change-skip')
      throw error
    }
  }

  /**
   * 切换租户
   * @param data 切换租户请求
   */
  async function switchTenant(data: SelectTenantRequest): Promise<void> {
    try {
      // 标记当前标签页已触发切换，避免 WS 通知导致重复刷新
      sessionStorage.setItem('tenant-change-skip', '1')
      const response = await authApi.selectTenant(data)
      const loginData = response.data.data
      // 同步可选租户列表（selectTenant 响应中包含完整租户列表）
      if (loginData.tenants && loginData.tenants.length > 0) {
        persistAvailableTenants(loginData.tenants)
      }
      await refreshLogin(loginData)
    } catch (error) {
      sessionStorage.removeItem('tenant-change-skip')
      throw error
    }
  }

  /**
   * 用户注册
   * @param registerData 注册信息
   */
  async function register(registerData: RegisterRequest): Promise<boolean> {
    try {
      const response = await authApi.register(registerData)
      return response.data.data
    } catch (error) {
      throw error
    }
  }

  /**
   * 刷新Token
   */
  async function refreshAccessToken(): Promise<void> {
    try {
      const response = await authApi.refreshToken(refreshToken.value)
      const data = response.data.data
      await refreshLogin(data)
    } catch (error) {
      // 刷新失败则退出登录
      await logout()
      throw error
    }
  }

  /**
   * 用户退出
   */
  async function logout(): Promise<void> {
    try {
      await authApi.logout()
    } catch (error) {
      // 即使请求失败也要清除本地数据
      console.error('Logout API failed:', error)
    } finally {
      // 清除所有状态
      clearUserData()
    }
  }

  /**
   * 修改密码
   * @param data 密码修改信息
   */
  async function changePassword(data: ChangePasswordRequest): Promise<boolean> {
    try {
      const response = await authApi.changePassword(data)
      return response.data.data
    } catch (error) {
      throw error
    }
  }

  /**
   * 修改个人信息
   * @param data 个人信息
   */
  async function updateProfile(data: UpdateProfileRequest): Promise<boolean> {
    try {
      const response = await authApi.updateProfile(data)
      if (response.data.data && userInfo.value) {
        // 更新本地用户信息
        setUserInfo({
          ...userInfo.value,
          ...data,
        })
      }
      return response.data.data
    } catch (error) {
      throw error
    }
  }

  /**
   * 获取用户详情（通过ID）
   * @param id 用户ID
   */
  async function fetchAccountDetail(id: string): Promise<AccountVO> {
    try {
      const response = await accountApi.detail(id)
      return response.data.data
    } catch (error) {
      throw error
    }
  }

  /**
   * 获取当前用户信息
   * @param userId 用户ID（可选，如果不传则使用当前用户ID）
   */
  async function fetchCurrentUserInfo(userId?: string): Promise<void> {
    const targetId = userId || userInfo.value?.id
    if (!targetId) {
      return
    }
    try {
      const data = await fetchAccountDetail(targetId as string)
      setUserInfo(data)
    } catch (error) {
      throw error
    }
  }

  /**
   * 设置用户信息（用于页面刷新后从localStorage恢复）
   * @param account 用户信息
   */
  function setUserInfo(account: AccountVO): void {
    userInfo.value = account
    setUser(userInfo.value)
  }

  /**
   * 清除用户数据
   */
  function clearUserData(): void {
    userInfo.value = null
    accessToken.value = ''
    refreshToken.value = ''
    currentTenant.value = null
    availableTenants.value = []
    needSelectTenant.value = false
    removeToken()
    removeRefreshToken()
    removeUser()
    cache.local.remove(TENANT_KEY)
    cache.local.remove(TENANTS_KEY)
  }

  /**
   * 检查当前租户角色是否为指定角色
   * @param role 租户角色
   */
  function hasRole(role: TenantRole): boolean {
    return tenantRole.value === role
  }

  /**
   * 检查当前租户角色是否在指定角色列表中
   * @param roleList 角色列表
   */
  function hasAnyRole(roleList: TenantRole[]): boolean {
    if (!tenantRole.value) return false
    return roleList.includes(tenantRole.value as TenantRole)
  }

  /**
   * 检查当前租户角色是否在指定角色列表中
   * @param roleList 角色列表
   */
  function hasAllRoles(roleList: TenantRole[]): boolean {
    if (!tenantRole.value) return false
    return roleList.includes(tenantRole.value as TenantRole)
  }

  /**
   * 初始化Store（从localStorage恢复token、用户和租户信息）
   */
  function initStore(): void {
    const token = getToken()
    const refresh = getRefreshToken()
    const user = getUser()
    const tenant = cache.local.getJSON(TENANT_KEY, {}) || null
    const tenants = cache.local.getJSON(TENANTS_KEY, [])

    if (token) {
      accessToken.value = token
    }
    if (refresh) {
      refreshToken.value = refresh
    }
    if (user) {
      userInfo.value = user
    }
    if (tenant) {
      currentTenant.value = tenant as TenantInfo
    }
    if (tenants && Array.isArray(tenants)) {
      availableTenants.value = tenants as TenantInfo[]
    }
  }

  // 自动初始化
  initStore()

  return {
    // State
    userInfo,
    accessToken,
    refreshToken,
    isLoggedIn,
    isAdmin,
    canEdit,
    isReadOnly,
    currentTenant,
    availableTenants,
    needSelectTenant,
    tenantRole,

    // Actions
    setRefresh,
    getRefresh,
    login,
    register,
    refreshLogin,
    refreshAccessToken,
    logout,
    changePassword,
    updateProfile,
    fetchAccountDetail,
    fetchCurrentUserInfo,
    setUserInfo,
    setAccessInfo,
    selectTenant,
    switchTenant,
    clearUserData,
    hasRole,
    hasAnyRole,
    hasAllRoles,
    initStore,
    persistTenant
  }
})
