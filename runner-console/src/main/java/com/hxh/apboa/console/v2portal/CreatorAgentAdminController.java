package com.hxh.apboa.console.v2portal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hxh.apboa.agent.service.AgentDefinitionService;
import com.hxh.apboa.common.consts.CreatorAgentConst;
import com.hxh.apboa.common.entity.ModelConfig;
import com.hxh.apboa.common.entity.SkillPackage;
import com.hxh.apboa.common.enums.AgentType;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.common.util.UserUtils;
import com.hxh.apboa.common.vo.AgentDefinitionVO;
import com.hxh.apboa.model.service.ModelConfigService;
import com.hxh.apboa.skill.service.SkillPackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@RestController
@RequestMapping("/v2-portal/creator-admin")
@RequiredArgsConstructor
public class CreatorAgentAdminController {
    private static final String BUILDER_SKILL_NAME = "apboa-agent-builder";

    private final AgentDefinitionService agentDefinitionService;
    private final ModelConfigService modelConfigService;
    private final SkillPackageService skillPackageService;

    @GetMapping
    public R<ConfigResponse> getConfig() {
        requirePlatformAdmin();
        return inSourceTenant(() -> {
            AgentDefinitionVO agent =
                    agentDefinitionService.agentDefinitionDetail(CreatorAgentConst.AGENT_ID);
            agent.setSystemPrompt(repairLegacyEncoding(agent.getSystemPrompt()));
            List<ModelConfig> models = modelConfigService.list(
                    new LambdaQueryWrapper<ModelConfig>()
                            .eq(ModelConfig::getEnabled, true)
                            .orderByAsc(ModelConfig::getName));
            List<SkillPackage> skills = skillPackageService.list(
                    new LambdaQueryWrapper<SkillPackage>()
                            .eq(SkillPackage::getEnabled, true)
                            .orderByAsc(SkillPackage::getName));
            ensureCreatorBuilderSkill(agent, skills);
            return R.data(new ConfigResponse(agent, models, skills));
        });
    }

    @PutMapping
    public R<Boolean> updateConfig(@RequestBody AgentDefinitionVO request) {
        requirePlatformAdmin();
        return inSourceTenant(() -> {
            AgentDefinitionVO agent =
                    agentDefinitionService.agentDefinitionDetail(CreatorAgentConst.AGENT_ID);
            List<SkillPackage> skills = skillPackageService.list(
                    new LambdaQueryWrapper<SkillPackage>()
                            .eq(SkillPackage::getEnabled, true)
                            .orderByAsc(SkillPackage::getName));
            mergeEditableConfig(agent, request, skills);
            agentDefinitionService.updateAgentDefinition(agent);
            return R.data(true);
        });
    }

    void mergeEditableConfig(AgentDefinitionVO agent, AgentDefinitionVO request) {
        mergeEditableConfig(agent, request, List.of());
    }

    void mergeEditableConfig(AgentDefinitionVO agent, AgentDefinitionVO request, List<SkillPackage> availableSkills) {
        agent.setAgentType(AgentType.CUSTOM);
        agent.setName(request.getName());
        agent.setDescription(request.getDescription());
        agent.setModelConfigId(request.getModelConfigId());
        agent.setModelParamsOverride(request.getModelParamsOverride());
        agent.setSkill(withCreatorBuilderSkill(request.getSkill(), availableSkills));
        agent.setTool(nullToEmpty(request.getTool()));
        agent.setMcp(nullToEmpty(request.getMcp()));
        agent.setMcpBindings(request.getMcpBindings() == null ? List.of() : request.getMcpBindings());
        agent.setHook(nullToEmpty(request.getHook()));
        agent.setSubAgent(nullToEmpty(request.getSubAgent()));
        agent.setKnowledgeBase(nullToEmpty(request.getKnowledgeBase()));
        agent.setToolChoiceStrategy(request.getToolChoiceStrategy());
        agent.setSpecificToolName(request.getSpecificToolName());
        agent.setSystemPromptTemplateId(request.getSystemPromptTemplateId());
        agent.setFollowTemplate(Boolean.TRUE.equals(request.getFollowTemplate()));
        agent.setSystemPrompt(repairLegacyEncoding(request.getSystemPrompt()));
        agent.setSensitiveWordConfigId(request.getSensitiveWordConfigId());
        agent.setSensitiveFilterEnabled(Boolean.TRUE.equals(request.getSensitiveFilterEnabled()));
        agent.setMaxIterations(request.getMaxIterations());
        agent.setEnablePlanning(Boolean.TRUE.equals(request.getEnablePlanning()));
        agent.setMaxSubtasks(request.getMaxSubtasks());
        agent.setRequirePlanConfirmation(Boolean.TRUE.equals(request.getRequirePlanConfirmation()));
        agent.setShowToolProcess(Boolean.TRUE.equals(request.getShowToolProcess()));
        agent.setEnableMemory(Boolean.TRUE.equals(request.getEnableMemory()));
        agent.setEnableMemoryCompression(Boolean.TRUE.equals(request.getEnableMemoryCompression()));
        agent.setMemoryCompressionConfig(request.getMemoryCompressionConfig());
        agent.setStructuredOutputEnabled(Boolean.TRUE.equals(request.getStructuredOutputEnabled()));
        agent.setStructuredOutputReminder(request.getStructuredOutputReminder());
        agent.setStructuredOutputSchema(request.getStructuredOutputSchema());
        agent.setVersion(request.getVersion());
        agent.setTag(request.getTag());
        agent.setStudioConfigId(request.getStudioConfigId());
        agent.setCodeExecutionConfigId(request.getCodeExecutionConfigId());
        agent.setLongTermMemoryConfigId(request.getLongTermMemoryConfigId());
    }

    void ensureCreatorBuilderSkill(AgentDefinitionVO agent, List<SkillPackage> availableSkills) {
        agent.setSkill(withCreatorBuilderSkill(agent.getSkill(), availableSkills));
    }

    List<Long> withCreatorBuilderSkill(List<Long> skillIds, List<SkillPackage> availableSkills) {
        LinkedHashSet<Long> merged = new LinkedHashSet<>(nullToEmpty(skillIds));
        availableSkills.stream()
                .filter(skill -> BUILDER_SKILL_NAME.equals(skill.getName()))
                .findFirst()
                .map(SkillPackage::getId)
                .ifPresent(merged::add);
        return new ArrayList<>(merged);
    }

    private List<Long> nullToEmpty(List<Long> value) {
        return value == null ? List.of() : value;
    }

    private void requirePlatformAdmin() {
        if (!UserUtils.isPlatformAdmin()) {
            throw new SecurityException("仅平台 admin 可配置小图");
        }
    }

    private String repairLegacyEncoding(String value) {
        String repaired = value;
        for (int i = 0; i < 3 && looksLikeMojibake(repaired); i++) {
            repaired = new String(
                    repaired.getBytes(StandardCharsets.ISO_8859_1),
                    StandardCharsets.UTF_8);
        }
        return repaired;
    }

    private boolean looksLikeMojibake(String value) {
        return value != null && (
                value.contains("Ã")
                        || value.contains("Â")
                        || value.contains("ä")
                        || value.contains("å")
                        || value.contains("æ")
                        || value.contains("ç")
                        || value.contains("è")
                        || value.contains("é"));
    }

    private <T> T inSourceTenant(java.util.function.Supplier<T> action) {
        Long originalTenantId = TenantUtils.getCurrentTenantId();
        String originalTenantCode = TenantUtils.getCurrentTenantCode();
        try {
            TenantUtils.setCurrentTenant(CreatorAgentConst.SOURCE_TENANT_ID, null);
            return action.get();
        } finally {
            TenantUtils.setCurrentTenant(originalTenantId, originalTenantCode);
        }
    }

    public record ConfigResponse(
            AgentDefinitionVO agent,
            List<ModelConfig> models,
            List<SkillPackage> skills) {
    }
}
