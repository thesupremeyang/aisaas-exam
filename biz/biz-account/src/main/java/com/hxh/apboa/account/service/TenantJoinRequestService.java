package com.hxh.apboa.account.service;

import com.hxh.apboa.common.entity.TenantJoinRequest;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 租户加入申请服务
 *
 * @author huxuehao
 */
public interface TenantJoinRequestService extends IService<TenantJoinRequest> {

    /**
     * 查询租户的待审批申请
     */
    List<TenantJoinRequest> listPendingByTenantId(Long tenantId);

    /**
     * 查询租户的所有申请
     */
    List<TenantJoinRequest> listByTenantId(Long tenantId);

    /**
     * 查询用户对指定租户的申请记录
     */
    TenantJoinRequest getByAccountAndTenant(Long accountId, Long tenantId);

    /**
     * 用户申请加入租户
     */
    TenantJoinRequest submitRequest(Long accountId, Long tenantId, String message);

    /**
     * 审批通过
     */
    void approve(Long requestId, Long reviewerId);

    /**
     * 审批拒绝
     */
    void reject(Long requestId, Long reviewerId);

    /**
     * 查询用户的所有申请记录
     */
    List<TenantJoinRequest> listByAccountId(Long accountId);

    /**
     * 撤销加入申请
     */
    void cancelRequest(Long requestId);
}
