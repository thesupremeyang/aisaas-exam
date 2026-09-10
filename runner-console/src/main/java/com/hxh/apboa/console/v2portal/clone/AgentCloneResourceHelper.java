package com.hxh.apboa.console.v2portal.clone;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hxh.apboa.agent.service.CodeExecutionConfigService;
import com.hxh.apboa.common.entity.*;
import com.hxh.apboa.common.enums.SkillFileType;
import com.hxh.apboa.common.enums.ToolType;
import com.hxh.apboa.common.vo.AgentDefinitionVO;
import com.hxh.apboa.console.v2portal.clone.mapper.AgentCrossTenantReadMapper;
import com.hxh.apboa.skill.SkillFileSystemService;
import com.hxh.apboa.skill.service.AgentSkillPackageService;
import com.hxh.apboa.skill.service.SkillFileService;
import com.hxh.apboa.skill.service.SkillPackageService;
import com.hxh.apboa.skill.service.SkillToolService;
import com.hxh.apboa.studio.service.AgentStudioService;
import com.hxh.apboa.studio.service.StudioConfigService;
import com.hxh.apboa.tool.service.ToolService;
import com.hxh.apboa.prompt.service.SystemPromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 智能体克隆资源复制辅助类
 * <p>
 * 负责处理提示词、技能包等关联资源的跨租户复制逻辑
 *
 * @author agent-clone-feature
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentCloneResourceHelper {

    private final AgentCrossTenantReadMapper crossReader;
    private final SkillPackageService skillPackageService;
    private final SkillFileService skillFileService;
    private final SkillToolService skillToolService;
    private final SystemPromptTemplateService systemPromptTemplateService;
    private final AgentSkillPackageService agentSkillPackageService;
    private final ToolService toolService;
    private final StudioConfigService studioConfigService;
    private final AgentStudioService agentStudioService;
    private final CodeExecutionConfigService codeExecutionConfigService;
    private final com.hxh.apboa.agent.service.AgentCodeExecutionService agentCodeExecutionService;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 在目标租户创建独立的提示词模板，并绑定到新智能体。
     *
     * @param source         源智能体
     * @param newVo          新智能体VO（会被修改）
     * @param targetTenantId 目标租户ID
     */
    public void copyAndBindPromptTemplate(AgentDefinition source,
                                          AgentDefinitionVO newVo,
                                          Long targetTenantId) {
        SystemPromptTemplate sourceTemplate = null;
        if (source.getSystemPromptTemplateId() != null) {
            sourceTemplate = crossReader.selectPromptTemplateByIdIgnoreTenant(source.getSystemPromptTemplateId());
        }

        String content = source.getSystemPrompt();
        if (Boolean.TRUE.equals(source.getFollowTemplate())
                && sourceTemplate != null
                && sourceTemplate.getContent() != null) {
            content = sourceTemplate.getContent();
        } else if (content == null && sourceTemplate != null) {
            content = sourceTemplate.getContent();
        }

        SystemPromptTemplate newTemplate = new SystemPromptTemplate();
        newTemplate.setTenantId(targetTenantId);
        newTemplate.setName(buildPromptTemplateName(newVo.getName()));
        newTemplate.setCategory(sourceTemplate == null ? null : sourceTemplate.getCategory());
        newTemplate.setDescription(sourceTemplate == null
                ? "由智能体「" + source.getName() + "」复制生成"
                : sourceTemplate.getDescription());
        newTemplate.setContent(content == null ? "" : content);
        newTemplate.setUsageCount(0);
        boolean saved = systemPromptTemplateService.save(newTemplate);
        if (!saved || newTemplate.getId() == null) {
            throw new IllegalStateException("创建复制的系统提示词模板失败");
        }

        newVo.setSystemPromptTemplateId(newTemplate.getId());
        newVo.setFollowTemplate(true);
        newVo.setSystemPrompt(newTemplate.getContent());

        log.info("[AgentClone] 独立提示词模板已创建并绑定: sourceAgentId={}, templateId={}, tenantId={}",
                source.getId(), newTemplate.getId(), targetTenantId);
    }

    private String buildPromptTemplateName(String agentName) {
        String suffix = " - 系统提示词-" + UUID.randomUUID().toString().substring(0, 8);
        int maxAgentNameLength = 100 - suffix.length();
        String safeAgentName = agentName.length() <= maxAgentNameLength
                ? agentName
                : agentName.substring(0, maxAgentNameLength);
        return safeAgentName + suffix;
    }

    /**
     * 深度复制技能包到目标租户
     * <p>
     * 复制内容包括：skill_package主表、skill_file文件表、skill_tool工具关联（仅内置工具）
     * 自定义工具不复制，需要用户后续手动配置
     *
     * @param sourceAgentId 源智能体ID
     * @param newAgentId    新智能体ID
     * @param targetTenantId 目标租户ID
     * @return 新复制的技能包ID列表（用于建立与新agent的关联）
     */
    public List<Long> copySkills(Long sourceAgentId, Long newAgentId, Long targetTenantId) {
        List<Long> newSkillIds = new ArrayList<>();

        // 1. 查询源智能体关联的所有技能包ID
        List<Long> sourceSkillIds = crossReader.selectSkillPackageIdsByAgentIdIgnoreTenant(sourceAgentId);
        if (sourceSkillIds == null || sourceSkillIds.isEmpty()) {
            log.info("[AgentClone] 源智能体无关联技能包，跳过技能复制");
            return newSkillIds;
        }

        log.info("[AgentClone] 开始复制技能包: sourceAgentId={}, sourceSkillIds={}", sourceAgentId, sourceSkillIds);

        // 2. 查询目标租户下已存在的内置工具，建立classPath->id的映射
        Map<String, Long> targetBuiltinToolMap = buildTargetBuiltinToolMap(targetTenantId);

        // 3. 逐个复制技能包
        for (Long sourceSkillId : sourceSkillIds) {
            try {
                Long newSkillId = copySingleSkill(sourceSkillId, targetTenantId, targetBuiltinToolMap);
                if (newSkillId != null) {
                    newSkillIds.add(newSkillId);

                    // 建立新agent与新技能包的关联
                    AgentSkillPackage agentSkill = new AgentSkillPackage();
                    agentSkill.setId(IdWorker.getId());
                    agentSkill.setAgentDefinitionId(newAgentId);
                    agentSkill.setSkillPackageId(newSkillId);
                    agentSkillPackageService.save(agentSkill);
                }
            } catch (Exception e) {
                log.error("[AgentClone] 复制技能包失败: sourceSkillId={}", sourceSkillId, e);
                // 单个技能包复制失败不影响整体流程
            }
        }

        log.info("[AgentClone] 技能包复制完成: newSkillIds={}", newSkillIds);
        return newSkillIds;
    }

    /**
     * 复制单个技能包
     */
    private Long copySingleSkill(Long sourceSkillId, Long targetTenantId, Map<String, Long> targetBuiltinToolMap) {
        // 1. 跨租户读取源技能包
        SkillPackage sourceSkill = crossReader.selectSkillPackageByIdIgnoreTenant(sourceSkillId);
        if (sourceSkill == null) {
            log.warn("[AgentClone] 源技能包不存在: sourceSkillId={}", sourceSkillId);
            return null;
        }

        // 2. 创建新技能包记录
        SkillPackage newSkill = new SkillPackage();
        BeanUtils.copyProperties(sourceSkill, newSkill);
        newSkill.setId(IdWorker.getId());
        newSkill.setTenantId(targetTenantId);
        newSkill.setName(generateUniqueSkillName(sourceSkill.getName(), targetTenantId));
        // 审计字段重置
        newSkill.setCreatedAt(null);
        newSkill.setUpdatedAt(null);
        newSkill.setCreatedBy(null);
        newSkill.setUpdatedBy(null);
        skillPackageService.save(newSkill);

        log.info("[AgentClone] 技能包主表已复制: sourceId={}, newId={}, newName={}",
                sourceSkillId, newSkill.getId(), newSkill.getName());

        // 3. 复制技能文件（兜底：源 skill 若缺少 SKILL.md 则自动生成）
        copySkillFiles(sourceSkillId, newSkill.getId(), newSkill.getName(), newSkill.getDescription());

        // 4. 复制工具关联（仅内置工具）
        copySkillTools(sourceSkillId, newSkill.getId(), targetTenantId, targetBuiltinToolMap);

        return newSkill.getId();
    }

    /**
     * 复制技能文件到DB和文件系统
     * <p>
     * 兜底逻辑：如果源 skill 包没有 SKILL.md 文件（AgentScope 框架要求必须存在），
     * 自动生成一份默认的 SKILL.md，避免新 agent 在聊天时因找不到 skill content 而报错。
     */
    private void copySkillFiles(Long sourceSkillId, Long newSkillId, String newSkillName, String newSkillDescription) {
        List<SkillFile> sourceFiles = crossReader.selectSkillFilesBySkillIdIgnoreTenant(sourceSkillId);

        // 先创建技能包目录
        SkillFileSystemService.buildSkillDir(newSkillName);

        // 检查源 skill 是否包含 SKILL.md
        boolean hasSkillMd = sourceFiles != null && sourceFiles.stream()
                .anyMatch(f -> f.getFileType() == SkillFileType.SKILL_MD);

        // 复制源文件
        int copiedCount = 0;
        if (sourceFiles != null && !sourceFiles.isEmpty()) {
            for (SkillFile sourceFile : sourceFiles) {
                SkillFile newFile = new SkillFile();
                BeanUtils.copyProperties(sourceFile, newFile);
                newFile.setId(IdWorker.getId());
                newFile.setSkillId(newSkillId);
                // tenantId由MyBatis-Plus自动填充，置null避免复制源租户ID
                newFile.setTenantId(null);
                // 审计字段重置
                newFile.setCreatedAt(null);
                newFile.setUpdatedAt(null);
                newFile.setCreatedBy(null);
                newFile.setUpdatedBy(null);
                skillFileService.save(newFile);

                // 同步写入文件系统
                if (sourceFile.getContent() != null) {
                    boolean writeOk = SkillFileSystemService.writeFile(newSkillName, sourceFile.getFilePath(), sourceFile.getContent());
                    if (!writeOk) {
                        log.error("[AgentClone] 写入技能文件失败: skillId={}, filePath={}", newSkillId, sourceFile.getFilePath());
                    }
                }
                copiedCount++;
            }
        }

        // 兜底：如果源 skill 没有 SKILL.md，自动生成一个默认的
        if (!hasSkillMd) {
            String defaultContent = SkillFileSystemService.buildSkillMdContent(newSkillName, newSkillDescription);
            SkillFile newSkillMd = new SkillFile();
            newSkillMd.setId(IdWorker.getId());
            newSkillMd.setTenantId(null);
            newSkillMd.setSkillId(newSkillId);
            newSkillMd.setFileType(SkillFileType.SKILL_MD);
            newSkillMd.setFileName("SKILL.md");
            newSkillMd.setFilePath("SKILL.md");
            newSkillMd.setContent(defaultContent);
            newSkillMd.setSort(0);
            newSkillMd.setEnabled(true);
            skillFileService.save(newSkillMd);
            // 同步到文件系统
            boolean writeOk = SkillFileSystemService.writeFile(newSkillName, "SKILL.md", defaultContent);
            if (!writeOk) {
                log.error("[AgentClone] 写入默认SKILL.md失败: skillId={}, skillName={}", newSkillId, newSkillName);
            }
            copiedCount++;
            log.warn("[AgentClone] 源 skill 缺少 SKILL.md，已自动生成默认值: skillId={}, skillName={}",
                    newSkillId, newSkillName);
        }

        log.info("[AgentClone] 技能文件已复制: skillId={}, fileCount={}", newSkillId, copiedCount);
    }

    /**
     * 复制技能工具关联（仅内置工具，自定义工具跳过）
     */
    private void copySkillTools(Long sourceSkillId, Long newSkillId, Long targetTenantId,
                                Map<String, Long> targetBuiltinToolMap) {
        List<SkillTool> sourceTools = crossReader.selectSkillToolsBySkillIdIgnoreTenant(sourceSkillId);
        if (sourceTools == null || sourceTools.isEmpty()) {
            return;
        }

        List<Long> validToolIds = new ArrayList<>();

        for (SkillTool sourceTool : sourceTools) {
            Long sourceToolId = sourceTool.getToolId();
            // 跨租户读取源工具信息
            ToolConfig sourceToolConfig = crossReadToolConfig(sourceToolId);
            if (sourceToolConfig == null) {
                continue;
            }

            // 只处理内置工具
            if (sourceToolConfig.getToolType() == ToolType.BUILTIN) {
                // 通过classPath在目标租户匹配
                String classPath = sourceToolConfig.getClassPath();
                Long targetToolId = targetBuiltinToolMap.get(classPath);
                if (targetToolId != null) {
                    validToolIds.add(targetToolId);
                    log.debug("[AgentClone] 内置工具匹配成功: classPath={}, targetToolId={}", classPath, targetToolId);
                } else {
                    log.warn("[AgentClone] 目标租户未找到匹配的内置工具: classPath={}", classPath);
                }
            } else {
                // 自定义工具，暂不复制，记录日志
                log.info("[AgentClone] 自定义工具不复制: sourceToolId={}, toolName={}", sourceToolId, sourceToolConfig.getName());
            }
        }

        // 批量保存工具关联
        if (!validToolIds.isEmpty()) {
            skillToolService.saveSkillTool(newSkillId, validToolIds);
            log.info("[AgentClone] 技能工具关联已复制: skillId={}, validToolCount={}", newSkillId, validToolIds.size());
        }
    }

    /**
     * 跨租户读取工具配置（使用JdbcTemplate直接查询，绕过拦截器）
     */
    private ToolConfig crossReadToolConfig(Long toolId) {
        try {
            List<ToolConfig> list = jdbcTemplate.query(
                    "SELECT id, tenant_id, name, tool_id, description, category, tool_type, class_path, language, code, version, scope_type, enabled, created_at, updated_at, created_by, updated_by FROM tool_config WHERE id = ?",
                    (rs, rowNum) -> {
                        ToolConfig tc = new ToolConfig();
                        tc.setId(rs.getLong("id"));
                        tc.setTenantId(rs.getLong("tenant_id"));
                        tc.setName(rs.getString("name"));
                        tc.setToolId(rs.getString("tool_id"));
                        tc.setDescription(rs.getString("description"));
                        tc.setCategory(rs.getString("category"));
                        String toolTypeStr = rs.getString("tool_type");
                        if (toolTypeStr != null) {
                            tc.setToolType(ToolType.valueOf(toolTypeStr));
                        }
                        tc.setClassPath(rs.getString("class_path"));
                        return tc;
                    },
                    toolId
            );
            return list.isEmpty() ? null : list.get(0);
        } catch (Exception e) {
            log.warn("[AgentClone] 跨租户读取工具配置失败: toolId={}, err={}", toolId, e.getMessage());
            return null;
        }
    }

    /**
     * 构建目标租户内置工具的classPath -> id映射
     */
    private Map<String, Long> buildTargetBuiltinToolMap(Long targetTenantId) {
        List<ToolConfig> targetTools = toolService.list(
                new LambdaQueryWrapper<ToolConfig>()
                        .eq(ToolConfig::getTenantId, targetTenantId)
                        .eq(ToolConfig::getToolType, ToolType.BUILTIN)
        );

        if (targetTools == null || targetTools.isEmpty()) {
            return Collections.emptyMap();
        }

        return targetTools.stream()
                .filter(tc -> tc.getClassPath() != null)
                .collect(Collectors.toMap(ToolConfig::getClassPath, ToolConfig::getId, (a, b) -> a));
    }

    /**
     * 生成唯一的技能包名称，避免重名
     */
    private String generateUniqueSkillName(String sourceName, Long targetTenantId) {
        String baseName = sourceName + "-copy";
        String finalName = baseName;
        int suffix = 1;

        // 检查名称是否存在，存在则加数字后缀
        while (isSkillNameExists(finalName, targetTenantId)) {
            finalName = baseName + "-" + suffix;
            suffix++;
            // 防止无限循环，最多尝试100次
            if (suffix > 100) {
                finalName = baseName + "-" + UUID.randomUUID().toString().substring(0, 8);
                break;
            }
        }

        return finalName;
    }

    /**
     * 检查技能包名称是否存在
     */
    private boolean isSkillNameExists(String name, Long tenantId) {
        Long count = skillPackageService.lambdaQuery()
                .eq(SkillPackage::getTenantId, tenantId)
                .eq(SkillPackage::getName, name)
                .count();
        return count != null && count > 0;
    }

    /**
     * 深度复制Studio配置到目标租户并建立关联
     *
     * @param sourceAgentId 源智能体ID
     * @param newAgentId    新智能体ID
     * @param targetTenantId 目标租户ID
     * @return 新的Studio配置ID，没有则返回null
     */
    public Long copyStudioConfig(Long sourceAgentId, Long newAgentId, Long targetTenantId) {
        try {
            Long sourceConfigId = crossReader.selectStudioConfigIdByAgentIdIgnoreTenant(sourceAgentId);
            if (sourceConfigId == null) {
                log.info("[AgentClone] 源智能体无Studio配置，跳过");
                return null;
            }

            StudioConfig sourceConfig = crossReader.selectStudioConfigByIdIgnoreTenant(sourceConfigId);
            if (sourceConfig == null) {
                log.warn("[AgentClone] 源Studio配置不存在: configId={}", sourceConfigId);
                return null;
            }

            // 创建新的Studio配置
            StudioConfig newConfig = new StudioConfig();
            BeanUtils.copyProperties(sourceConfig, newConfig);
            newConfig.setId(IdWorker.getId());
            newConfig.setTenantId(targetTenantId);
            // url和project直接复制，无敏感信息
            studioConfigService.save(newConfig);

            // 建立新agent与新配置的关联
            agentStudioService.saveAgentStudio(newAgentId, List.of(newConfig.getId()));

            log.info("[AgentClone] Studio配置已复制: sourceId={}, newId={}", sourceConfigId, newConfig.getId());
            return newConfig.getId();
        } catch (Exception e) {
            log.error("[AgentClone] 复制Studio配置失败: sourceAgentId={}", sourceAgentId, e);
            return null;
        }
    }

    /**
     * 深度复制代码执行环境配置到目标租户并建立关联
     *
     * @param sourceAgentId 源智能体ID
     * @param newAgentId    新智能体ID
     * @param targetTenantId 目标租户ID
     * @return 新的代码执行配置ID，没有则返回null
     */
    public Long copyCodeExecutionConfig(Long sourceAgentId, Long newAgentId, Long targetTenantId) {
        try {
            Long sourceConfigId = crossReader.selectCodeExecutionConfigIdByAgentIdIgnoreTenant(sourceAgentId);
            if (sourceConfigId == null) {
                log.info("[AgentClone] 源智能体无代码执行配置，跳过");
                return null;
            }

            CodeExecutionConfig sourceConfig = crossReader.selectCodeExecutionConfigByIdIgnoreTenant(sourceConfigId);
            if (sourceConfig == null) {
                log.warn("[AgentClone] 源代码执行配置不存在: configId={}", sourceConfigId);
                return null;
            }

            // 创建新的代码执行配置
            CodeExecutionConfig newConfig = new CodeExecutionConfig();
            BeanUtils.copyProperties(sourceConfig, newConfig);
            newConfig.setId(IdWorker.getId());
            newConfig.setTenantId(targetTenantId);
            // 审计字段重置
            newConfig.setCreatedAt(null);
            newConfig.setUpdatedAt(null);
            newConfig.setCreatedBy(null);
            newConfig.setUpdatedBy(null);
            codeExecutionConfigService.save(newConfig);

            // 建立新agent与新配置的关联
            agentCodeExecutionService.saveAgentCodeExecution(newAgentId, List.of(newConfig.getId()));

            log.info("[AgentClone] 代码执行配置已复制: sourceId={}, newId={}", sourceConfigId, newConfig.getId());
            return newConfig.getId();
        } catch (Exception e) {
            log.error("[AgentClone] 复制代码执行配置失败: sourceAgentId={}", sourceAgentId, e);
            return null;
        }
    }
}
