package com.hxh.apboa.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 待审批加入申请信息（登录被阻时返回）
 *
 * @author huxuehao
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingApprovalInfo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 申请ID
     */
    private Long requestId;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 租户名称
     */
    private String tenantName;

    /**
     * 租户编码
     */
    private String tenantCode;

    /**
     * 申请状态
     */
    private String status;

    /**
     * 申请创建时间
     */
    private LocalDateTime createdAt;
}
