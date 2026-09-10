package com.hxh.apboa.common.entity;

import com.hxh.apboa.common.config.SerializableEnable;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.enums.McpToolExposureMode;
import lombok.*;

/**
 * 智能体与MCP服务器关联
 *
 * @author huxuehao
 */
@Getter
@Setter
@TableName(TableConst.AGENT_MCP)
@AllArgsConstructor
@NoArgsConstructor
public class AgentMcpServer implements SerializableEnable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;
    private Long agentDefinitionId;
    private Long mcpServerId;
    private McpToolExposureMode exposureMode;
}
