package com.hxh.apboa.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 租户加入申请状态
 *
 * @author huxuehao
 */
@Getter
@AllArgsConstructor
public enum TenantJoinRequestStatus {
    PENDING("待审批"),
    APPROVED("已通过"),
    REJECTED("已拒绝"),
    CANCELLED("已撤销");

    private final String description;
}
