package com.hxh.apboa.common.entity;

import com.hxh.apboa.common.config.SerializableEnable;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.enums.TenantJoinRequestStatus;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 租户加入申请表
 *
 * @author huxuehao
 */
@Getter
@Setter
@TableName(TableConst.TENANT_JOIN_REQUEST)
public class TenantJoinRequest implements SerializableEnable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;
    private Long accountId;
    private TenantJoinRequestStatus status;
    private String message;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
