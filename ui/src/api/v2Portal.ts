/**
 * v2 门户专用 API
 *
 * <p>与原 agent.ts 的区别：v2 智能体广场固定访问 tenant_id=1 的数据，
 * 不受当前登录用户所属租户影响。</p>
 *
 * <p>对应后端：{@code V2PortalExploreController}，
 * 路径前缀 {@code /api/v2-portal/explore}。</p>
 *
 * @author huxuehao
 */
import request from '@/utils/request'
import type { AgentDefinitionDTO, AgentDefinitionVO, ModelConfigVO, SkillPackageVO } from '@/types'
import type { ApiResponse, PageResult } from '@/types'


const BASE = '/api/v2-portal/explore'

/**
 * 智能体广场分页查询
 * 后端会强制把租户上下文切换为 tenant_id=1
 */
export function pageByPortal(query: AgentDefinitionDTO) {
  return request.get<ApiResponse<PageResult<AgentDefinitionVO>>>(
    `${BASE}/page`,
    { params: query }
  )
}
/**
 * 智能体广场智能体详情（强制 tenant_id=1）
 */
export function detailByPortal(id: string) {
  return request.get<ApiResponse<AgentDefinitionVO>>(`${BASE}/detail/${id}`)
}

/**
 * 智能体广场标签列表（强制 tenant_id=1）
 */
export function listTagsByPortal() {
  return request.get<ApiResponse<string[]>>(`${BASE}/tags`)
}

/** V4 创建卡片使用的固定平台创建助手。 */
export function creatorAgentDetail() {
  return request.get<ApiResponse<AgentDefinitionVO>>(`${BASE}/creator`)
}

export interface CreatorAdminConfig {
  agent: AgentDefinitionVO
  models: ModelConfigVO[]
  skills: SkillPackageVO[]
}

export function creatorAdminConfig() {
  return request.get<ApiResponse<CreatorAdminConfig>>('/api/v2-portal/creator-admin')
}

export function updateCreatorAdminConfig(data: AgentDefinitionVO) {
  return request.put<ApiResponse<boolean>>('/api/v2-portal/creator-admin', data)
}
