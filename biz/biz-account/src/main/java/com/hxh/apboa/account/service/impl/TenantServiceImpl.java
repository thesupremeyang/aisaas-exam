package com.hxh.apboa.account.service.impl;

import com.hxh.apboa.account.mapper.TenantMapper;
import com.hxh.apboa.account.service.AccountTenantService;
import com.hxh.apboa.account.service.TenantInitService;
import com.hxh.apboa.account.service.TenantService;
import com.hxh.apboa.common.dto.TenantSettingsDTO;
import com.hxh.apboa.common.entity.AccountTenant;
import com.hxh.apboa.common.entity.Tenant;
import com.hxh.apboa.common.enums.TenantRole;
import com.hxh.apboa.common.exception.BusinessException;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.common.util.UserUtils;
import com.hxh.apboa.common.vo.TenantDiscoveryVO;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 租户服务实现
 *
 * @author huxuehao
 */
@Service
@RequiredArgsConstructor
public class TenantServiceImpl extends ServiceImpl<TenantMapper, Tenant> implements TenantService {

    private final AccountTenantService accountTenantService;
    private final TenantInitService tenantInitService;

    @Override
    public Tenant getByCode(String code) {
        return lambdaQuery().eq(Tenant::getCode, code).one();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Tenant createTenant(Tenant tenant, Long creatorAccountId) {
        // 初始化默认治理值
        if (tenant.getDiscoverable() == null) tenant.setDiscoverable(false);
        if (tenant.getJoinable() == null) tenant.setJoinable(false);
        if (tenant.getJoinApprovalRequired() == null) tenant.setJoinApprovalRequired(true);

        // 保存租户
        save(tenant);

        // 将创建者添加为租户管理员
        AccountTenant membership = new AccountTenant();
        membership.setId(IdWorker.getId());
        membership.setAccountId(creatorAccountId);
        membership.setTenantId(tenant.getId());
        membership.setRole(TenantRole.TENANT_OWNER);
        accountTenantService.save(membership);

        // 初始化种子数据（params、内置 tools/MCP/skills 等）
        tenantInitService.initTenantData(tenant.getId());

        return tenant;
    }

    @Override
    public List<TenantDiscoveryVO> listDiscoverable(Long accountId) {
        Long currentTenantId = UserUtils.getTenantId();

        // 1. 获取用户已加入的租户关联（无论 discoverable 是否为 true）
        List<AccountTenant> memberships = accountTenantService.listByAccountId(accountId);
        Set<Long> joinedTenantIds = memberships.stream()
                .map(AccountTenant::getTenantId)
                .collect(Collectors.toSet());

        // 构建 tenantId -> AccountTenant 映射，便于后续获取角色
        Map<Long, AccountTenant> membershipMap = memberships.stream()
                .collect(Collectors.toMap(AccountTenant::getTenantId, m -> m, (a, b) -> a));

        // 2. 查询所有已加入的租户详情
        List<Tenant> joinedTenants = Collections.emptyList();
        if (!joinedTenantIds.isEmpty()) {
            joinedTenants = lambdaQuery()
                    .in(Tenant::getId, joinedTenantIds)
                    .eq(Tenant::getEnabled, true)
                    .list();
        }

        // 3. 查询可发现但未加入的租户
        List<Tenant> discoverableTenants = lambdaQuery()
                .eq(Tenant::getDiscoverable, true)
                .eq(Tenant::getEnabled, true)
                .notIn(!joinedTenantIds.isEmpty(), Tenant::getId, joinedTenantIds)
                .list();

        // 4. 构建结果列表：已加入的排前面，当前登录租户排最前
        List<TenantDiscoveryVO> result = new ArrayList<>();

        for (Tenant t : joinedTenants) {
            result.add(toDiscoveryVO(t, true,
                    membershipMap.get(t.getId()).getRole().name(),
                    t.getId().equals(currentTenantId)));
        }

        for (Tenant t : discoverableTenants) {
            result.add(toDiscoveryVO(t, false, null, false));
        }

        // 已加入的按当前租户优先排序
        result.sort(Comparator.comparing((TenantDiscoveryVO v) -> !Boolean.TRUE.equals(v.getIsCurrent())));

        return result;
    }

    @Override
    public List<TenantDiscoveryVO> listPassAuthDiscoverable() {
        List<Tenant> discoverableTenants = lambdaQuery()
                .eq(Tenant::getDiscoverable, true)
                .eq(Tenant::getJoinable, true)
                .eq(Tenant::getEnabled, true)
                .list();

        List<TenantDiscoveryVO> result = new ArrayList<>();
        for (Tenant t : discoverableTenants) {
            result.add(toDiscoveryVO(t, false, null, false));
        }

        return result;
    }

    /**
     * 构建 TenantDiscoveryVO
     */
    private TenantDiscoveryVO toDiscoveryVO(Tenant t, boolean isJoined, String role, boolean isCurrent) {
        TenantDiscoveryVO vo = new TenantDiscoveryVO();
        vo.setId(t.getId());
        vo.setName(t.getName());
        vo.setCode(t.getCode());
        vo.setDescription(t.getDescription());
        vo.setContactName(t.getContactName());
        vo.setContactEmail(t.getContactEmail());
        vo.setDiscoverable(t.getDiscoverable());
        vo.setJoinable(t.getJoinable());
        vo.setJoinApprovalRequired(t.getJoinApprovalRequired());
        vo.setIsJoined(isJoined);
        vo.setRole(role);
        vo.setIsCurrent(isCurrent);
        return vo;
    }

    @Override
    public Tenant updateSettings(Long tenantId, TenantSettingsDTO settings) {
        Tenant tenant = getById(tenantId);
        if (tenant == null) {
            throw new BusinessException("租户不存在");
        }

        if (settings.getDiscoverable() != null) tenant.setDiscoverable(settings.getDiscoverable());
        if (settings.getJoinable() != null) tenant.setJoinable(settings.getJoinable());
        if (settings.getJoinApprovalRequired() != null) tenant.setJoinApprovalRequired(settings.getJoinApprovalRequired());
        if (!FuncUtils.isEmpty(settings.getName())) tenant.setName(settings.getName());
        if (settings.getDescription() != null) tenant.setDescription(settings.getDescription());
        if (settings.getContactName() != null) tenant.setContactName(settings.getContactName());
        if (settings.getContactEmail() != null) tenant.setContactEmail(settings.getContactEmail());

        updateById(tenant);
        return tenant;
    }

    @Override
    public void addMember(Long tenantId, Long accountId, String role) {
        // 检查是否已是成员
        AccountTenant existing = accountTenantService.getByAccountAndTenant(accountId, tenantId);
        if (existing != null) {
            throw new BusinessException("该用户已是该租户的成员");
        }

        AccountTenant membership = new AccountTenant();
        membership.setId(IdWorker.getId());
        membership.setAccountId(accountId);
        membership.setTenantId(tenantId);
        membership.setRole(TenantRole.valueOf(role));
        accountTenantService.save(membership);
    }
}
