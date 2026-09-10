package com.hxh.apboa.console.v2portal.clone.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 克隆子表关系专用 mapper
 * <p>
 * 用于方案 G：从源 agent 复制子表关系到新 agent。
 * 因为源子表行的 tenant_id = 源 agent 所属租户，而我们要插入新行时
 * tenant_id = caller 租户，所以不能直接 INSERT...SELECT（会被拦截器过滤）。
 * <p>
 * 解决：在 mapper 方法上加 @InterceptorIgnore(tenantLine = "true") 绕开
 * 拦截器，但 SELECT 部分仍按 source_agent_definition_id 精确过滤源行。
 * <p>
 * 严格约束：INSERT 的源行筛选条件必须包含 sourceAgentId；禁止不带条件的全表复制。
 *
 * @author agent-clone-feature
 */
@Mapper
public interface AgentSubItemCloneMapper {

    // ─── hooks ─────────────────────────────────────────────────────────────────

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT hook_config_id FROM agent_hooks WHERE agent_definition_id = #{sourceAgentId}")
    List<Long> selectHookConfigIdsByAgentId(@Param("sourceAgentId") Long sourceAgentId);

    @InterceptorIgnore(tenantLine = "true")
    @Insert("INSERT INTO agent_hooks (id, tenant_id, agent_definition_id, hook_config_id) VALUES (#{id}, #{tenantId}, #{agentDefinitionId}, #{hookConfigId})")
    int insertHookRow(@Param("id") Long id, @Param("tenantId") Long tenantId, @Param("agentDefinitionId") Long agentDefinitionId, @Param("hookConfigId") Long hookConfigId);

    // ─── tools ─────────────────────────────────────────────────────────────────

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT tool_id FROM agent_tools WHERE agent_definition_id = #{sourceAgentId}")
    List<Long> selectToolIdsByAgentId(@Param("sourceAgentId") Long sourceAgentId);

    @InterceptorIgnore(tenantLine = "true")
    @Insert("INSERT INTO agent_tools (id, tenant_id, agent_definition_id, tool_id) VALUES (#{id}, #{tenantId}, #{agentDefinitionId}, #{toolId})")
    int insertToolRow(@Param("id") Long id, @Param("tenantId") Long tenantId, @Param("agentDefinitionId") Long agentDefinitionId, @Param("toolId") Long toolId);

    // ─── sub_agents ────────────────────────────────────────────────────────────

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT sub_agent_id FROM agent_sub_agents WHERE parent_agent_id = #{sourceAgentId}")
    List<Long> selectSubAgentIdsByAgentId(@Param("sourceAgentId") Long sourceAgentId);

    @InterceptorIgnore(tenantLine = "true")
    @Insert("INSERT INTO agent_sub_agents (id, tenant_id, parent_agent_id, sub_agent_id) VALUES (#{id}, #{tenantId}, #{parentAgentId}, #{subAgentId})")
    int insertSubAgentRow(@Param("id") Long id, @Param("tenantId") Long tenantId, @Param("parentAgentId") Long parentAgentId, @Param("subAgentId") Long subAgentId);
}
