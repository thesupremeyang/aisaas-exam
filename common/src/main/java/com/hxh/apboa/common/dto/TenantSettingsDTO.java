package com.hxh.apboa.common.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 租户治理设置请求
 *
 * @author huxuehao
 */
@Data
public class TenantSettingsDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 是否允许被发现 */
    private Boolean discoverable;
    /** 是否允许申请加入 */
    private Boolean joinable;
    /** 加入是否需要审批 */
    private Boolean joinApprovalRequired;
    /** 租户名称 */
    private String name;
    /** 租户描述 */
    private String description;
    /** 联系人 */
    private String contactName;
    /** 联系邮箱 */
    private String contactEmail;
}
