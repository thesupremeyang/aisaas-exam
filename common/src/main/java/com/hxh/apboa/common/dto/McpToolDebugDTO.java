package com.hxh.apboa.common.dto;

import com.hxh.apboa.common.config.SerializableEnable;
import java.util.Map;
import lombok.Data;

/**
 * MCP 工具调试调用请求
 *
 * @author huxuehao
 */
@Data
public class McpToolDebugDTO implements SerializableEnable {

    /**
     * 工具 ID
     */
    private Long toolId;

    /**
     * 工具调用参数
     */
    private Map<String, Object> input;
}
