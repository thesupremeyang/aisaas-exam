package com.hxh.apboa.account.service.impl;

import com.hxh.apboa.account.mapper.AccountTenantMapper;
import com.hxh.apboa.account.service.AccountTenantService;
import com.hxh.apboa.common.entity.AccountTenant;
import com.hxh.apboa.common.enums.TenantRole;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hxh.apboa.common.enums.WsMessageType;
import com.hxh.apboa.common.message.TenantChangeMessage;
import com.hxh.apboa.common.ws.WebSocketClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 账号-租户关联服务实现
 *
 * @author huxuehao
 */
@Service
@RequiredArgsConstructor
public class AccountTenantServiceImpl extends ServiceImpl<AccountTenantMapper, AccountTenant> implements AccountTenantService {

    private final WebSocketClient webSocketClient;

    @Override
    public List<AccountTenant> listByAccountId(Long accountId) {
        return lambdaQuery()
                .eq(AccountTenant::getAccountId, accountId)
                .eq(AccountTenant::getEnabled, true)
                .list();
    }

    @Override
    public AccountTenant getByAccountAndTenant(Long accountId, Long tenantId) {
        return lambdaQuery()
                .eq(AccountTenant::getAccountId, accountId)
                .eq(AccountTenant::getTenantId, tenantId)
                .eq(AccountTenant::getEnabled, true)
                .one();
    }

    @Override
    public List<AccountTenant> listByTenantId(Long tenantId) {
        return lambdaQuery()
                .eq(AccountTenant::getTenantId, tenantId)
                .eq(AccountTenant::getEnabled, true)
                .list();
    }

    @Override
    public boolean updateRole(Long accountId, Long tenantId, TenantRole role) {
        boolean update = lambdaUpdate()
                .eq(AccountTenant::getAccountId, accountId)
                .eq(AccountTenant::getTenantId, tenantId)
                .set(AccountTenant::getRole, role)
                .update();
        if (update) {
            sendTenantChangeNotice(accountId.toString(), String.valueOf(tenantId), role);
        }
        return update;
    }

    /**
     * WebSocket 通知用户租户已切换（集群广播到所有标签页）
     */
    private void sendTenantChangeNotice(String accountId, String tenantId, TenantRole role) {
        TenantChangeMessage message =
                TenantChangeMessage.builder()
                        .accountId(accountId)
                        .tenantId(tenantId)
                        .role(role)
                        .build();
        webSocketClient.pushToUserCluster(accountId,
                WsMessageType.ACCOUNT_ROLE_CHANGE.name(), message);
    }
}
