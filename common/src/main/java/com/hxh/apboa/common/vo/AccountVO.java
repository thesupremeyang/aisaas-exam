package com.hxh.apboa.common.vo;

import com.hxh.apboa.common.config.SerializableEnable;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 账号VO
 *
 * @author huxuehao
 */
@Data
@EqualsAndHashCode
public class AccountVO implements SerializableEnable {
    private Long id;
    private String nickname;
    private String email;
    private String username;
    private Boolean enabled;
    /** 当前租户内的角色 */
    private String tenantRole;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
    private Boolean rememberLastTenant;
}
