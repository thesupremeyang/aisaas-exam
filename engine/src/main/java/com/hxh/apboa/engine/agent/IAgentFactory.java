package com.hxh.apboa.engine.agent;

import com.hxh.apboa.account.service.TenantService;
import com.hxh.apboa.agent.service.AgentDefinitionService;
import com.hxh.apboa.agent.mapper.SystemAgentMapper;
import com.hxh.apboa.common.consts.CreatorAgentConst;
import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.entity.Tenant;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.engine.agui.AgentContext;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.agent.Agent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 描述：智能体工厂类
 *
 * @author huxuehao
 **/
@Component
@RequiredArgsConstructor
public class IAgentFactory {
    private final TenantService tenantService;
    private final A2aAgentHelper a2aAgentHelper;
    private final ReActAgentHelper reActAgentHelper;
    private final AgentDefinitionService agentDefinitionService;
    private final SystemAgentMapper systemAgentMapper;

    /**
     * 根据Agent定义ID获取Agent
     *
     * @param agentId Agent定义ID
     * @param tenantId 租户ID
     */
    public Agent getAgent(Long agentId, Long tenantId) {
        try {
            boolean creatorAgent = CreatorAgentConst.AGENT_ID == agentId;
            AgentContext currentContext = AgentContext.getIfExists().orElse(null);
            Long callerTenantId = currentContext != null ? currentContext.getTenantId() : null;
            String callerTenantCode = currentContext != null ? currentContext.getTenantCode() : null;

            setTenantInfo(creatorAgent ? CreatorAgentConst.SOURCE_TENANT_ID : tenantId);
            AgentDefinition definition = creatorAgent
                    ? systemAgentMapper.selectByIdIgnoreTenant(agentId)
                    : agentDefinitionService.getById(agentId);
            validAgentDefinition(definition);
            if (creatorAgent) {
                // Keep AgentContext on the caller's tenant for tool/API calls,
                // but read the system agent's model and skills from its owner tenant.
                if (currentContext != null && callerTenantId != null) {
                    currentContext.setTenantId(callerTenantId);
                    currentContext.setTenantCode(callerTenantCode);
                }
            }

            return switch (definition.getAgentType()) {
                case CUSTOM -> getReActAgent(definition);
                case A2A -> getA2aAgent(definition);
                default -> throw new IllegalArgumentException("未知的智能体类型");
            };
        } catch (Exception e) {
            AgentContext.clean();
            throw new RuntimeException(e);
        } finally {
            TenantUtils.clear();
        }
    }

    /**
     * 根据Agent定义获取A2aAgent
     * @param definition Agent 定义
     */
    private A2aAgent getA2aAgent(AgentDefinition definition) {
        try {
            return a2aAgentHelper.getA2aAgent(definition);
        } catch (Exception e) {
            AgentContext.clean();
            throw new RuntimeException(e);
        }
    }

    /**
     * 根据Agent定义获取ReActAgent
     * @param definition Agent 定义
     */
    private ReActAgent getReActAgent(AgentDefinition definition) {
        try {
            return reActAgentHelper.getReActAgent(definition);
        } catch (Exception e) {
            AgentContext.clean();
            throw new RuntimeException(e);
        }
    }

    /**
     * 验证Agent定义
     * @param definition Agent定义
     */
    private void validAgentDefinition(AgentDefinition definition) {
        if (definition == null) {
            throw new RuntimeException("Agent not found" );
        }

        if (!definition.getEnabled()) {
            throw new RuntimeException("Agent is disabled, agentCode: " + definition.getAgentCode());
        }
    }

    /**
     * 设置租户信息
     * @param tenantId 租户ID
     */
    private void setTenantInfo(Long tenantId) {
        Tenant tenant = tenantService.getById(tenantId);
        if (tenant == null) {
            throw new IllegalArgumentException("租户不存在");
        }

        // 设置租户信息到AgentContext
        AgentContext agentContext = AgentContext.get();
        agentContext.setTenantId(tenant.getId());
        agentContext.setTenantCode(tenant.getCode());

        // 设置当前租户
        TenantUtils.setCurrentTenant(tenant.getId(), tenant.getCode());
    }
}
