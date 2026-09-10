package com.hxh.apboa.agent.service.impl;

import com.hxh.apboa.hook.service.impl.AgentHookServiceImpl;
import com.hxh.apboa.knowledge.service.impl.AgentKnowledgeBaseServiceImpl;
import com.hxh.apboa.skill.service.impl.AgentSkillPackageServiceImpl;
import com.hxh.apboa.tool.service.impl.AgentToolServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentOptionalBindingServiceTest {

    @Test
    void optionalAgentBindingsIgnoreNullIds() {
        assertTrue(assertDoesNotThrow(() -> new AgentHookServiceImpl().insertAgentHook(1L, null)));
        assertTrue(assertDoesNotThrow(() -> new AgentSubAgentServiceImpl().insertSubAgent(1L, null)));
        assertTrue(assertDoesNotThrow(() -> new AgentToolServiceImpl().insertAgentTool(1L, null)));
        assertTrue(assertDoesNotThrow(() -> new AgentSkillPackageServiceImpl().insertAgentSkillPackage(1L, null)));
        assertTrue(assertDoesNotThrow(() -> new AgentKnowledgeBaseServiceImpl().insertAgentKnowledge(1L, null)));
    }
}
