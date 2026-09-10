package com.hxh.apboa.common.vo;

import com.hxh.apboa.common.config.SerializableEnable;
import com.hxh.apboa.common.enums.TenantJoinRequestStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 租户加入申请VO（含申请人信息）
 *
 * @author huxuehao
 */
@Data
@EqualsAndHashCode
public class TenantJoinRequestVO implements SerializableEnable {
    private Long id;
    private Long tenantId;
    private Long accountId;
    /** 申请人昵称 */
    private String applicantName;
    /** 申请人用户名（账号） */
    private String applicantUsername;
    private TenantJoinRequestStatus status;
    private String message;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
