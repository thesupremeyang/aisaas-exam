package com.hxh.apboa.console.v2portal.clone;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.config.SerializableEnable;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * agent 广场克隆审计实体
 * <p>
 * 记录：谁（clonedBy）、何时（createdAt）、从哪个源 agent（sourceAgentId +
 * sourceTenantId）克隆、克隆出什么新 agent（newAgentId + newTenantId）。
 *
 * @author agent-clone-feature
 */
@Data
@TableName("agent_clone_record")
public class AgentCloneRecord implements SerializableEnable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 源 agent 主键 */
    private Long sourceAgentId;

    /** 源 agent 所属租户 */
    private Long sourceTenantId;

    /** 克隆后的新 agent 主键 */
    private Long newAgentId;

    /** 新 agent 所属租户（caller 租户） */
    private Long newTenantId;

    /** 克隆发起人 userId */
    private Long clonedBy;

    /** 源 agent_code（冗余） */
    private String sourceAgentCode;

    /** 新 agent_code（冗余） */
    private String newAgentCode;

    /** 克隆时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
