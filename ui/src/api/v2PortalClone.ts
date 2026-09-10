/**
 * v2 门户克隆 agent API
 *
 * <p>对应后端：{@code AgentCloneController}，
 * 路径 {@code POST /api/v2-portal/clone/agent}。</p>
 *
 * <p>业务语义：把"立即使用"从"借 default 跑"改为"克隆到当前用户租户"。
 * 调用方租户由后端从 {@code TenantUtils.getCurrentTenantId()} 读取。</p>
 *
 * @author agent-clone-feature
 */
import request from '@/utils/request'

/** 克隆响应 */
export interface AgentCloneResponse {
  id: string
  name: string
  agentCode: string
  tenantId: number
  enabled: boolean
  sourceAgentId: string
}

/** 统一响应包装 */
export interface AgentCloneApiResponse<T> {
  code: number
  success: boolean
  msg: string
  data: T
}

/**
 * 克隆源 agent 到当前用户所在租户
 *
 * @param sourceAgentId 源 agent 主键
 */
export function cloneAgentByPortal(sourceAgentId: string | number) {
  return request.post<AgentCloneApiResponse<AgentCloneResponse>>(
    '/api/v2-portal/clone/agent',
    { sourceAgentId }
  )
}
