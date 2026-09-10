package com.hxh.apboa.common.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 注册请求DTO
 *
 * @author huxuehao
 */
@Data
public class RegisterRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 是否同时创建租户
     */
    private Boolean createTenant;

    /**
     * 租户名称（createTenant=true时必填）
     */
    private String tenantName;

    /**
     * 租户编码（createTenant=true时必填）
     */
    private String tenantCode;

    /**
     * 租户描述
     */
    private String tenantDescription;

    /**
     * 要加入的租户ID（与 createTenant 互斥）
     */
    private Long joinTenantId;

    /**
     * 加入申请附言（可选）
     */
    private String joinMessage;
}
