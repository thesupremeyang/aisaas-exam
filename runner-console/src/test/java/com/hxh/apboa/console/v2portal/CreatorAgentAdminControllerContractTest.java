package com.hxh.apboa.console.v2portal;

import com.hxh.apboa.common.vo.AgentDefinitionVO;
import com.hxh.apboa.common.vo.AgentMcpBindingVO;
import com.hxh.apboa.common.entity.SkillPackage;
import com.hxh.apboa.common.enums.McpToolExposureMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class CreatorAgentAdminControllerContractTest {

    @Test
    void updateConfigAcceptsFullAgentDefinition() {
        assertDoesNotThrow(() ->
                CreatorAgentAdminController.class.getMethod("updateConfig", AgentDefinitionVO.class));
    }

    @Test
    void mergeEditableConfigKeepsPlatformAgentIdentity() {
        CreatorAgentAdminController controller = new CreatorAgentAdminController(null, null, null);
        AgentDefinitionVO persisted = new AgentDefinitionVO();
        persisted.setId(1L);
        persisted.setTenantId(10L);
        persisted.setAgentCode("creator-agent");
        persisted.setEnabled(true);

        AgentDefinitionVO request = new AgentDefinitionVO();
        request.setId(2L);
        request.setTenantId(20L);
        request.setAgentCode("wrong-code");
        request.setEnabled(false);
        request.setName("小图");
        request.setDescription("平台创建助手");
        request.setModelConfigId(101L);
        request.setSkill(List.of(201L, 202L));
        request.setMcp(List.of(301L));
        request.setTool(List.of(401L));
        request.setEnablePlanning(true);
        request.setMaxIterations(80);
        request.setMaxSubtasks(12);
        request.setShowToolProcess(true);
        request.setCodeExecutionConfigId(501L);
        AgentMcpBindingVO binding = new AgentMcpBindingVO();
        binding.setMcpServerId(301L);
        binding.setExposureMode(McpToolExposureMode.SELECTED_ONLY);
        binding.setMcpToolIds(List.of(302L, 303L));
        request.setMcpBindings(List.of(binding));
        request.setSystemPrompt("新的系统提示词");
        request.setFollowTemplate(false);

        controller.mergeEditableConfig(persisted, request);

        assertEquals(1L, persisted.getId());
        assertEquals(10L, persisted.getTenantId());
        assertEquals("creator-agent", persisted.getAgentCode());
        assertEquals(true, persisted.getEnabled());
        assertEquals(101L, persisted.getModelConfigId());
        assertEquals(List.of(201L, 202L), persisted.getSkill());
        assertEquals(List.of(301L), persisted.getMcp());
        assertEquals(List.of(401L), persisted.getTool());
        assertEquals(1, persisted.getMcpBindings().size());
        assertEquals(McpToolExposureMode.SELECTED_ONLY, persisted.getMcpBindings().getFirst().getExposureMode());
        assertEquals(List.of(302L, 303L), persisted.getMcpBindings().getFirst().getMcpToolIds());
        assertEquals(true, persisted.getEnablePlanning());
        assertEquals(80, persisted.getMaxIterations());
        assertEquals(12, persisted.getMaxSubtasks());
        assertEquals(true, persisted.getShowToolProcess());
        assertEquals(501L, persisted.getCodeExecutionConfigId());
        assertEquals("新的系统提示词", persisted.getSystemPrompt());
    }

    @Test
    void mergeEditableConfigKeepsCreatorBuilderSkill() {
        CreatorAgentAdminController controller = new CreatorAgentAdminController(null, null, null);
        AgentDefinitionVO persisted = new AgentDefinitionVO();
        persisted.setSkill(List.of(201L));

        SkillPackage creatorSkill = new SkillPackage();
        creatorSkill.setId(202L);
        creatorSkill.setName("apboa-agent-builder");

        AgentDefinitionVO request = new AgentDefinitionVO();
        request.setSkill(List.of());

        controller.mergeEditableConfig(persisted, request, List.of(creatorSkill));

        assertEquals(List.of(202L), persisted.getSkill());
    }
}
