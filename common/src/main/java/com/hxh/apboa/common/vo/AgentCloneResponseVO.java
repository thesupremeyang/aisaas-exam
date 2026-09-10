package com.hxh.apboa.common.vo;

import com.hxh.apboa.common.config.SerializableEnable;
import lombok.Data;

/**
 * 广场克隆 agent 响应 VO
 * <p>
 * 返回新 agent 的关键信息，前端跳转到新 agent 的 chat 页。
 *
 * @author agent-clone-feature
 */
@Data
public class AgentCloneResponseVO implements SerializableEnable {
    /**
     * 新 agent 主键
     */
    private Long id;
    /**
     * 新 agent 名称（源名称 + " (副本)"）
     */
    private String name;
    /**
     * 新 agent_code（源 agentCode + "-copy-" + UUID8）
     */
    private String agentCode;
    /**
     * 新 agent 所属租户 id（调用方租户）
     */
    private Long tenantId;
    /**
     * 新 agent 是否启用（克隆后默认 false）
     */
    private Boolean enabled;
    /**
     * 源 agent 主键（方便前端回显）
     */
    private Long sourceAgentId;
}
