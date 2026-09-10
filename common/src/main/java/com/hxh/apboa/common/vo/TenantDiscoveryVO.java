package com.hxh.apboa.common.vo;

import java.io.Serializable;

import lombok.Data;

/**
 * 租户发现视图对象 — 包含当前用户的成员身份信息
 *
 * @author huxuehao
 */
@Data
public class TenantDiscoveryVO implements Serializable {

    /** 租户ID */
    private Long id;
    /** 租户名称 */
    private String name;
    /** 租户编码 */
    private String code;
    /** 租户描述 */
    private String description;
    /** 联系人 */
    private String contactName;
    /** 联系邮箱 */
    private String contactEmail;
    /** 是否允许被发现 */
    private Boolean discoverable;
    /** 是否允许申请加入 */
    private Boolean joinable;
    /** 加入是否需要审批 */
    private Boolean joinApprovalRequired;
    /** 当前用户是否已是该租户成员 */
    private Boolean isJoined;
    /** 当前用户在该租户中的角色（已加入时有值） */
    private String role;
    /** 是否为当前登录的租户 */
    private Boolean isCurrent;
}
