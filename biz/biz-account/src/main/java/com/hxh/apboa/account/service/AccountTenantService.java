package com.hxh.apboa.account.service;

import com.hxh.apboa.common.entity.AccountTenant;
import com.hxh.apboa.common.enums.TenantRole;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 账号-租户关联服务
 *
 * @author huxuehao
 */
public interface AccountTenantService extends IService<AccountTenant> {

    /**
     * 查询账号所属的所有租户
     */
    List<AccountTenant> listByAccountId(Long accountId);

    /**
     * 查询账号在指定租户中的关联
     */
    AccountTenant getByAccountAndTenant(Long accountId, Long tenantId);

    /**
     * 查询租户下的所有成员
     */
    List<AccountTenant> listByTenantId(Long tenantId);

    /**
     * 修改成员角色
     */
    boolean updateRole(Long accountId, Long tenantId, TenantRole role);
}
