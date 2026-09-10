package com.hxh.apboa.account.service;

import com.hxh.apboa.common.dto.TenantSettingsDTO;
import com.hxh.apboa.common.entity.Tenant;
import com.hxh.apboa.common.vo.TenantDiscoveryVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 租户服务
 *
 * @author huxuehao
 */
public interface TenantService extends IService<Tenant> {

    /**
     * 根据编码查询租户
     */
    Tenant getByCode(String code);

    /**
     * 创建租户并初始化基础数据
     */
    Tenant createTenant(Tenant tenant, Long creatorAccountId);

    /**
     * 列出可被发现的租户（含已加入的租户及成员身份信息）
     *
     * @param accountId 当前用户ID
     */
    List<TenantDiscoveryVO> listDiscoverable(Long accountId);

    /**
     * 列出可被发现的租户（含已加入的租户及成员身份信息）
     */
    List<TenantDiscoveryVO> listPassAuthDiscoverable();

    /**
     * 更新租户治理设置
     */
    Tenant updateSettings(Long tenantId, TenantSettingsDTO settings);

    /**
     * 直接添加成员到租户
     */
    void addMember(Long tenantId, Long accountId, String role);
}
