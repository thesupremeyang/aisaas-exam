package com.hxh.apboa.agent.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.hxh.apboa.common.entity.AgentDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SystemAgentMapper {
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT id, tenant_id, agent_type, name, agent_code, description, model_config_id, model_params_override, tool_choice_strategy, specific_tool_name, system_prompt_template_id, follow_template, system_prompt, sensitive_word_config_id, sensitive_filter_enabled, max_iterations, enable_planning, show_tool_process, max_subtasks, require_plan_confirmation, enable_memory, enable_memory_compression, memory_compression_config, structured_output_enabled, structured_output_reminder, structured_output_schema, version, tag, enabled, created_at, updated_at, created_by, updated_by FROM agent_definition WHERE id = #{id}")
    AgentDefinition selectByIdIgnoreTenant(@Param("id") Long id);
}
