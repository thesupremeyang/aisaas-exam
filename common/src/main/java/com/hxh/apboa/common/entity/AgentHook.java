package com.hxh.apboa.common.entity;

import com.hxh.apboa.common.config.SerializableEnable;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.consts.TableConst;
import lombok.*;

/**
 * 智能体与Hook关联
 *
 * @author huxuehao
 */
@Getter
@Setter
@TableName(TableConst.AGENT_HOOKS)
@AllArgsConstructor
@NoArgsConstructor
public class AgentHook implements SerializableEnable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;
    private Long agentDefinitionId;
    private Long hookConfigId;
}
