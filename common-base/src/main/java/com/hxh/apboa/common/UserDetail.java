package com.hxh.apboa.common;

import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 描述：用户详情
 *
 * @author huxuehao
 **/
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserDetail implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long id;
    // 昵称
    private String name;
    // 账号
    private String username;
    // 邮箱
    private String email;
    // 当前会话选中的租户ID
    private Long tenantId;
    // 当前租户编码
    private String tenantCode;
    // 当前租户内的角色
    private String tenantRole;
    // 当前租户名称
    private String tenantName;
    // 用户可访问的租户列表
    private List<TenantInfo> tenants;

    /**
     * 租户简要信息（用于切换器展示）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantInfo implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long tenantId;
        private String tenantCode;
        private String tenantName;
        private String role;
    }
}
