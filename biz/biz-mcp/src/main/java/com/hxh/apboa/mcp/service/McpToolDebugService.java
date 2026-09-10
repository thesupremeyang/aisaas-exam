package com.hxh.apboa.mcp.service;

import com.hxh.apboa.common.vo.McpToolDebugResultVO;
import java.util.Map;

/**
 * MCP 工具调试 Service
 *
 * @author huxuehao
 */
public interface McpToolDebugService {

    /**
     * 调试调用 MCP 工具
     *
     * @param toolId 工具 ID
     * @param input  工具调用参数
     * @return 调试结果
     */
    McpToolDebugResultVO debugTool(Long toolId, Map<String, Object> input);
}
