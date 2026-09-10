package com.hxh.apboa.engine.prompt;

import com.hxh.apboa.common.consts.CreatorAgentConst;
import com.hxh.apboa.common.entity.AgentDefinition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSysPromptFactoryTest {

    @Test
    void creatorAgentPromptIncludesDeterministicBuildContract() {
        AgentSysPromptFactory factory = new AgentSysPromptFactory(
                new ArrayList<>(List.of(new FixedPrompt("base prompt"))),
                null);
        AgentDefinition creator = new AgentDefinition();
        creator.setId(CreatorAgentConst.AGENT_ID);
        creator.setAgentCode(CreatorAgentConst.AGENT_CODE);

        String prompt = factory.getAgentSysPrompt(creator, true);

        assertTrue(prompt.contains("CREATOR_AGENT_BUILD_CONTRACT"));
        assertTrue(prompt.contains("create_agent.py"));
        assertTrue(prompt.contains("python create_agent.py"));
        assertTrue(prompt.contains("NEVER use python -c"));
        assertTrue(prompt.contains("/agent/definition"));
        assertTrue(prompt.contains("Do not guess, probe, fuzz, or scan"));
        assertTrue(prompt.contains("智能体名称"));
        assertTrue(prompt.contains("智能体 ID"));
        assertTrue(prompt.contains("模型名称"));
        assertTrue(prompt.contains("分类"));
        assertTrue(prompt.contains("当前算力"));
        assertTrue(prompt.contains("使用技能"));
        assertTrue(prompt.contains("使用入口"));
    }

    @Test
    void nonCreatorAgentPromptDoesNotIncludeBuildContract() {
        AgentSysPromptFactory factory = new AgentSysPromptFactory(
                new ArrayList<>(List.of(new FixedPrompt("base prompt"))),
                null);
        AgentDefinition normal = new AgentDefinition();
        normal.setId(123L);
        normal.setAgentCode("normal-agent");

        String prompt = factory.getAgentSysPrompt(normal, true);

        assertFalse(prompt.contains("CREATOR_AGENT_BUILD_CONTRACT"));
    }

    private record FixedPrompt(String prompt) implements AgentSysPrompt {
        @Override
        public String getPrompt(AgentDefinition agentDefinition) {
            return prompt;
        }

        @Override
        public int order() {
            return 0;
        }
    }
}
