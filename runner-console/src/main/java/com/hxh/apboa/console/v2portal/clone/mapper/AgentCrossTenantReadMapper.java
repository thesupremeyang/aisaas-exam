package com.hxh.apboa.console.v2portal.clone.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.hxh.apboa.common.entity.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 跨租户只读 mapper
 * <p>
 * 用于方案 G：clone 接口要按主键读 source agent_definition，
 * 不受 TenantLineInnerInterceptor 的 tenant_id 过滤限制（因为源 agent
 * 属于其他租户）。
 * <p>
 * 严格约束：只允许 SELECT；禁止 INSERT/UPDATE/DELETE。
 *
 * @author agent-clone-feature
 */
@Mapper
public interface AgentCrossTenantReadMapper {

    /**
     * 按主键查 agent_definition，绕过租户过滤
     *
     * @param id 源 agent 主键
     * @return 源 agent；null 表示不存在
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT id, tenant_id, agent_type, name, agent_code, description, model_config_id, model_params_override, tool_choice_strategy, specific_tool_name, system_prompt_template_id, follow_template, system_prompt, sensitive_word_config_id, sensitive_filter_enabled, max_iterations, enable_planning, show_tool_process, max_subtasks, require_plan_confirmation, enable_memory, enable_memory_compression, memory_compression_config, structured_output_enabled, structured_output_reminder, structured_output_schema, version, tag, enabled, created_at, updated_at, created_by, updated_by FROM agent_definition WHERE id = #{id}")
    AgentDefinition selectByIdIgnoreTenant(@Param("id") Long id);

    /**
     * 按主键查系统提示词模板，绕过租户过滤
     *
     * @param id 模板ID
     * @return 模板实体；null 表示不存在
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT id, tenant_id, category, name, description, content, usage_count, enabled, created_at, updated_at, created_by, updated_by FROM system_prompt_template WHERE id = #{id}")
    SystemPromptTemplate selectPromptTemplateByIdIgnoreTenant(@Param("id") Long id);

    /**
     * 查询源智能体关联的技能包ID列表
     *
     * @param agentId 源智能体ID
     * @return 技能包ID列表
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT skill_package_id FROM agent_skill_packages WHERE agent_definition_id = #{agentId}")
    List<Long> selectSkillPackageIdsByAgentIdIgnoreTenant(@Param("agentId") Long agentId);

    /**
     * 按主键查技能包，绕过租户过滤
     *
     * @param id 技能包ID
     * @return 技能包实体；null 表示不存在
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT id, tenant_id, name, description, category, enabled, created_at, updated_at, created_by, updated_by FROM skill_package WHERE id = #{id}")
    SkillPackage selectSkillPackageByIdIgnoreTenant(@Param("id") Long id);

    /**
     * 查询技能包下的所有文件，绕过租户过滤
     *
     * @param skillId 技能包ID
     * @return 文件列表
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT id, tenant_id, skill_id, file_type, file_name, file_path, content, sort, enabled, created_at, updated_at, created_by, updated_by FROM skill_file WHERE skill_id = #{skillId} ORDER BY file_type, sort")
    List<SkillFile> selectSkillFilesBySkillIdIgnoreTenant(@Param("skillId") Long skillId);

    /**
     * 查询技能包关联的工具ID列表，绕过租户过滤
     *
     * @param skillId 技能包ID
     * @return 关联列表
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT id, tenant_id, skill_id, tool_id FROM skill_tools WHERE skill_id = #{skillId}")
    List<SkillTool> selectSkillToolsBySkillIdIgnoreTenant(@Param("skillId") Long skillId);

    /**
     * 查询源智能体关联的Studio配置ID
     *
     * @param agentId 源智能体ID
     * @return Studio配置ID
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT studio_id FROM agent_studio WHERE agent_definition_id = #{agentId} LIMIT 1")
    Long selectStudioConfigIdByAgentIdIgnoreTenant(@Param("agentId") Long agentId);

    /**
     * 按主键查Studio配置，绕过租户过滤
     *
     * @param id 配置ID
     * @return Studio配置实体；null 表示不存在
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT id, tenant_id, url, project FROM studio_config WHERE id = #{id}")
    StudioConfig selectStudioConfigByIdIgnoreTenant(@Param("id") Long id);

    /**
     * 查询源智能体关联的代码执行配置ID
     *
     * @param agentId 源智能体ID
     * @return 代码执行配置ID
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT code_execution_id FROM agent_code_execution WHERE agent_definition_id = #{agentId} LIMIT 1")
    Long selectCodeExecutionConfigIdByAgentIdIgnoreTenant(@Param("agentId") Long agentId);

    /**
     * 按主键查代码执行配置，绕过租户过滤
     *
     * @param id 配置ID
     * @return 代码执行配置实体；null 表示不存在
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT id, tenant_id, config_name, work_dir, upload_dir, auto_upload, enable_shell, enable_read, enable_write, command FROM code_execution_config WHERE id = #{id}")
    CodeExecutionConfig selectCodeExecutionConfigByIdIgnoreTenant(@Param("id") Long id);
}
