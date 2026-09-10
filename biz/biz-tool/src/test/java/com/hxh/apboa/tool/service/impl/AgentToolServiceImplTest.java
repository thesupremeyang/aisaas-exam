package com.hxh.apboa.tool.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolServiceImplTest {

    @Test
    void insertAgentToolIgnoresNullToolIds() {
        AgentToolServiceImpl service = new AgentToolServiceImpl();

        Boolean result = assertDoesNotThrow(() -> service.insertAgentTool(1L, null));

        assertTrue(result);
    }
}
