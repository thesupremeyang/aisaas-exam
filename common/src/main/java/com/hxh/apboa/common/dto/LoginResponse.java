package com.hxh.apboa.common.dto;

import com.hxh.apboa.common.UserDetail;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 登录响应DTO
 *
 * @author huxuehao
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 访问令牌
     */
    private String accessToken;

    /**
     * 到期时间（毫秒）
     */
    private Long accessTokenTTL;

    /**
     * 刷新令牌
     */
    private String refreshToken;

    /**
     * 到期时间（毫秒）
     */
    private Long refreshTokenTTL;

    /**
     * 用户详情
     */
    private UserDetail userDetail;

    /**
     * 是否需要选择租户
     */
    private Boolean needSelectTenant;

    /**
     * 可选择的租户列表（needSelectTenant = true 时返回）
     */
    private List<UserDetail.TenantInfo> tenants;

    /**
     * 登录是否被阻断（有待审批申请时返回 true）
     */
    private Boolean blocked;

    /**
     * 待审批的加入申请列表（blocked = true 时返回）
     */
    private List<PendingApprovalInfo> pendingApprovals;
}
