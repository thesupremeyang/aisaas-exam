package com.hxh.apboa.common.message;

import com.hxh.apboa.common.config.SerializableEnable;
import com.hxh.apboa.common.enums.TenantRole;
import lombok.*;

/**
 * 租户切换消息（WebSocket 通知）
 *
 * @author huxuehao
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TenantChangeMessage implements SerializableEnable {
    private String accountId;
    private String tenantId;
    private TenantRole role;
}
