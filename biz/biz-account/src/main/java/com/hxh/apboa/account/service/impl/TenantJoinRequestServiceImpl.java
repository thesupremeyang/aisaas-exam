package com.hxh.apboa.account.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hxh.apboa.account.mapper.TenantJoinRequestMapper;
import com.hxh.apboa.account.service.AccountTenantService;
import com.hxh.apboa.account.service.TenantJoinRequestService;
import com.hxh.apboa.common.entity.AccountTenant;
import com.hxh.apboa.common.entity.TenantJoinRequest;
import com.hxh.apboa.common.enums.TenantJoinRequestStatus;
import com.hxh.apboa.common.enums.TenantRole;
import com.hxh.apboa.common.exception.BusinessException;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 租户加入申请服务实现
 *
 * @author huxuehao
 */
@Service
@RequiredArgsConstructor
public class TenantJoinRequestServiceImpl extends ServiceImpl<TenantJoinRequestMapper, TenantJoinRequest>
        implements TenantJoinRequestService {

    private final AccountTenantService accountTenantService;

    @Override
    public List<TenantJoinRequest> listPendingByTenantId(Long tenantId) {
        return lambdaQuery()
                .eq(TenantJoinRequest::getTenantId, tenantId)
                .eq(TenantJoinRequest::getStatus, TenantJoinRequestStatus.PENDING)
                .orderByDesc(TenantJoinRequest::getCreatedAt)
                .list();
    }

    @Override
    public List<TenantJoinRequest> listByTenantId(Long tenantId) {
        return lambdaQuery()
                .eq(TenantJoinRequest::getTenantId, tenantId)
                .orderByDesc(TenantJoinRequest::getCreatedAt)
                .list();
    }

    @Override
    public TenantJoinRequest getByAccountAndTenant(Long accountId, Long tenantId) {
        return lambdaQuery()
                .eq(TenantJoinRequest::getAccountId, accountId)
                .eq(TenantJoinRequest::getTenantId, tenantId)
                .one();
    }

    @Override
    public TenantJoinRequest submitRequest(Long accountId, Long tenantId, String message) {
        // 检查是否已是该租户成员
        AccountTenant existing = accountTenantService.getByAccountAndTenant(accountId, tenantId);
        if (existing != null) {
            throw new BusinessException("您已是该租户的成员");
        }

        // 检查是否有待审批的申请
        TenantJoinRequest pending = lambdaQuery()
                .eq(TenantJoinRequest::getAccountId, accountId)
                .eq(TenantJoinRequest::getTenantId, tenantId)
                .eq(TenantJoinRequest::getStatus, TenantJoinRequestStatus.PENDING)
                .one();
        if (pending != null) {
            throw new BusinessException("您已提交过申请，请等待审批");
        }

        TenantJoinRequest request = new TenantJoinRequest();
        request.setId(IdWorker.getId());
        request.setTenantId(tenantId);
        request.setAccountId(accountId);
        request.setStatus(TenantJoinRequestStatus.PENDING);
        request.setMessage(message);
        save(request);
        return request;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long requestId, Long reviewerId) {
        TenantJoinRequest request = getById(requestId);
        if (request == null) {
            throw new BusinessException("申请不存在");
        }
        if (request.getStatus() != TenantJoinRequestStatus.PENDING) {
            throw new BusinessException("该申请已被处理");
        }

        // 更新申请状态
        request.setStatus(TenantJoinRequestStatus.APPROVED);
        request.setReviewedBy(reviewerId);
        request.setReviewedAt(LocalDateTime.now());
        updateById(request);

        // 检查是否已是租户成员（防止重复添加）
        AccountTenant existing = accountTenantService.getByAccountAndTenant(
                request.getAccountId(), request.getTenantId());
        if (existing != null) {
            return;
        }

        // 将用户加入租户（默认角色为查看者）
        AccountTenant membership = new AccountTenant();
        membership.setId(IdWorker.getId());
        membership.setAccountId(request.getAccountId());
        membership.setTenantId(request.getTenantId());
        membership.setRole(TenantRole.TENANT_VIEWER);
        accountTenantService.save(membership);
    }

    @Override
    public void reject(Long requestId, Long reviewerId) {
        TenantJoinRequest request = getById(requestId);
        if (request == null) {
            throw new BusinessException("申请不存在");
        }
        if (request.getStatus() != TenantJoinRequestStatus.PENDING) {
            throw new BusinessException("该申请已被处理");
        }

        request.setStatus(TenantJoinRequestStatus.REJECTED);
        request.setReviewedBy(reviewerId);
        request.setReviewedAt(LocalDateTime.now());
        updateById(request);
    }

    @Override
    public List<TenantJoinRequest> listByAccountId(Long accountId) {
        return lambdaQuery()
                .eq(TenantJoinRequest::getAccountId, accountId)
                .orderByDesc(TenantJoinRequest::getCreatedAt)
                .list();
    }

    @Override
    public void cancelRequest(Long requestId) {
        TenantJoinRequest request = getById(requestId);
        if (request == null) {
            throw new BusinessException("申请不存在");
        }
        if (request.getStatus() != TenantJoinRequestStatus.PENDING) {
            throw new BusinessException("该申请已被处理");
        }

        request.setStatus(TenantJoinRequestStatus.CANCELLED);
        request.setReviewedAt(LocalDateTime.now());
        updateById(request);
    }
}
