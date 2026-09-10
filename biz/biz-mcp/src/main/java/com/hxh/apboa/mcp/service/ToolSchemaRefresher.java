package com.hxh.apboa.mcp.service;

import com.hxh.apboa.common.entity.McpServer;
import com.hxh.apboa.mcp.ToolSchemaRefreshResult;

/**
 * 工具目录刷新器接口，由 core 模块实现以打破 mcp 与 core 的循环依赖。
 *
 * @author huxuehao
 */
public interface ToolSchemaRefresher {
    ToolSchemaRefreshResult refreshToolSchemas(McpServer server);
}
