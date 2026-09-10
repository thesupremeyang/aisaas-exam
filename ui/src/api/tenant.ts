import request from '@/utils/request'
import type {
  ApiResponse,
  PageResult,
} from '@/types'
import type {
  Tenant,
  AccountTenant,
  TenantJoinRequest,
  TenantVO,
  TenantMemberVO,
  TenantJoinRequestVO,
  TenantDTO,
  TenantCreateRequest,
  TenantSettingsRequest,
  TenantJoinRequestDTO,
  TenantMemberAddDTO,
  TenantDiscoveryVO,
} from '@/types'

/**
 * 分页查询
 * GET /tenant/list
 */
export function page(query: TenantDTO) {
  return request.get<ApiResponse<PageResult<TenantVO>>>('/api/tenant/list', {
    params: query
  })
}

/**
 * 列表查询
 * GET /tenant/list
 */
export function list() {
  return request.get<ApiResponse<Tenant[]>>('/api/tenant/list')
}

/**
 * 详情
 * GET /tenant/{id}
 */
export function detail(id: string) {
  return request.get<ApiResponse<Tenant>>(`/api/tenant/${id}`)
}

/**
 * 新增
 * POST /tenant
 */
export function save(data: TenantCreateRequest) {
  return request.post<ApiResponse<Tenant>>('/api/tenant', data)
}

/**
 * 修改
 * PUT /tenant/{id}
 */
export function update(id: string, data: Tenant) {
  return request.put<ApiResponse<boolean>>(`/api/tenant/${id}`, data)
}

/**
 * 删除
 * DELETE /tenant/{id}
 */
export function remove(id: string) {
  return request.delete<ApiResponse<boolean>>(`/api/tenant/${id}`)
}

/**
 * 发现可加入的租户列表
 * GET /tenant/discoverable
 */
export function listDiscoverable() {
  return request.get<ApiResponse<TenantDiscoveryVO[]>>('/api/tenant/discoverable')
}

/**
 * 发现可加入的租户列表
 * GET /tenant/pass-auth/discoverable
 */
export function listPassAuthDiscoverable() {
  return request.get<ApiResponse<TenantDiscoveryVO[]>>('/api/tenant/pass-auth/discoverable')
}

/**
 * 更新租户治理设置
 * PUT /tenant/{id}/settings
 */
export function updateSettings(id: string, data: TenantSettingsRequest) {
  return request.put<ApiResponse<Tenant>>(`/api/tenant/${id}/settings`, data)
}

/**
 * 成员列表
 * GET /tenant/{id}/members
 */
export function listMembers(id: string) {
  return request.get<ApiResponse<TenantMemberVO[]>>(`/api/tenant/${id}/members`)
}

/**
 * 添加成员
 * POST /tenant/{id}/members
 */
export function addMember(id: string, data: TenantMemberAddDTO) {
  return request.post<ApiResponse<boolean>>(`/api/tenant/${id}/members`, data)
}

/**
 * 修改成员角色
 * PUT /tenant/{id}/members/{accountId}/role
 */
export function updateMemberRole(tenantId: string, accountId: string, role: string) {
  return request.put<ApiResponse<boolean>>(
    `/api/tenant/${tenantId}/members/${accountId}/role`,
    null,
    { params: { role } }
  )
}

/**
 * 移除成员
 * DELETE /tenant/{id}/members/{accountId}
 */
export function removeMember(tenantId: string, accountId: string) {
  return request.delete<ApiResponse<boolean>>(`/api/tenant/${tenantId}/members/${accountId}`)
}

/**
 * 申请加入租户
 * POST /tenant/{id}/join-request
 */
export function joinRequest(tenantId: string, data: TenantJoinRequestDTO) {
  return request.post<ApiResponse<TenantJoinRequest>>(`/api/tenant/${tenantId}/join-request`, data)
}

/**
 * 查看租户的加入申请列表
 * GET /tenant/{id}/join-requests
 */
export function listJoinRequests(tenantId: string) {
  return request.get<ApiResponse<TenantJoinRequestVO[]>>(`/api/tenant/${tenantId}/join-requests`)
}

/**
 * 审批通过加入申请
 * PUT /tenant/join-request/{requestId}/approve
 */
export function approveJoinRequest(requestId: string) {
  return request.put<ApiResponse<boolean>>(`/api/tenant/join-request/${requestId}/approve`)
}

/**
 * 审批拒绝加入申请
 * PUT /tenant/join-request/{requestId}/reject
 */
export function rejectJoinRequest(requestId: string) {
  return request.put<ApiResponse<boolean>>(`/api/tenant/join-request/${requestId}/reject`)
}

/**
 * 撤销自己的加入申请
 * DELETE /tenant/join-request/{requestId}/cancel
 */
export function cancelJoinRequest(requestId: string) {
  return request.delete<ApiResponse<boolean>>(`/api/tenant/join-request/${requestId}/cancel`)
}

/**
 * 查看自己的所有加入申请记录
 * GET /tenant/my-join-requests
 */
export function listMyJoinRequests() {
  return request.get<ApiResponse<TenantJoinRequest[]>>('/api/tenant/my-join-requests')
}
