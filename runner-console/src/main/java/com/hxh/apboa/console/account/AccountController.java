package com.hxh.apboa.console.account;

import com.hxh.apboa.account.service.AccountService;
import com.hxh.apboa.common.config.auth.ChatKeyAccess;
import com.hxh.apboa.common.config.auth.RoleNeed;
import com.hxh.apboa.common.config.auth.SkAccess;
import com.hxh.apboa.common.consts.SysConst;
import com.hxh.apboa.common.dto.AccountDTO;
import com.hxh.apboa.common.dto.PlatformAccountCreateRequest;
import com.hxh.apboa.common.dto.RegisterRequest;
import com.hxh.apboa.common.entity.Account;
import com.hxh.apboa.common.enums.TenantRole;
import com.hxh.apboa.common.exception.BusinessException;
import com.hxh.apboa.common.mp.support.MP;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.common.util.BeanUtils;
import com.hxh.apboa.common.util.UserUtils;
import com.hxh.apboa.common.vo.AccountVO;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

/**
 * 账号Controller
 *
 * @author huxuehao
 */
@RestController
@RequestMapping("/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    /**
     * list查询
     */
    @GetMapping("/list")
    public R<List<AccountVO>> list(AccountDTO query) {
        List<Account> list = accountService.list(MP.getQueryWrapper(query));
        List<AccountVO> accounts = BeanUtils.copyList(list, AccountVO.class);
        return R.data(accounts);
    }

    /**
     * 详情
     */
    @SkAccess
    @ChatKeyAccess
    @GetMapping("/{id}")
    public R<AccountVO> detail(@PathVariable("id") Long id) {
        Account entity = accountService.getById(id);
        if (entity == null) {
            throw new BusinessException("用户不存在");
        }
        AccountVO account = BeanUtils.copy(entity, AccountVO.class);
        return R.data(account);
    }

    /**
     * 新增
     */
    @PostMapping
    @RoleNeed({TenantRole.TENANT_ADMIN})
    public R<Boolean> save(@RequestBody Account entity) {
        RegisterRequest request = BeanUtils.copy(entity, RegisterRequest.class);
        // 管理端创建用户时，自动加入当前管理员的租户（免审批，作为查看者）
        request.setJoinTenantId(UserUtils.getTenantId());
        return R.data(accountService.register(request));
    }

    /**
     * 删除
     */
    @DeleteMapping
    @RoleNeed({TenantRole.TENANT_ADMIN})
    @Transactional(rollbackFor = Exception.class)
    public R<Boolean> delete(@RequestBody List<Long> ids) {
        for (Account account : accountService.listByIds(ids)) {
            if (Objects.equals(account.getId(), SysConst.ADMIN_ACCOUNT_ID)) {
                throw new BusinessException("管理员账号不可删除");
            }
        }
        return R.data(accountService.removeByIds(ids));
    }

    /**
     * 禁用/激活用户
     */
    @RoleNeed({TenantRole.TENANT_ADMIN})
    @PutMapping("/{id}/toggle-enabled")
    public R<Boolean> toggleEnabled(@PathVariable("id") Long id, @RequestParam("enabled") Boolean enabled) {
        return R.data(accountService.toggleEnabled(id, enabled));
    }

    /**
     * 管理员修改用户密码
     */
    @RoleNeed({TenantRole.TENANT_ADMIN})
    @PutMapping("/{id}/change-password")
    public R<Boolean> adminChangePassword(@PathVariable("id") Long id, @RequestParam("newPassword") String newPassword) {
        return R.data(accountService.adminChangePassword(id, newPassword));
    }

    // ==================== 用户管理（仅平台管理员，标记由账号表 platform_admin 驱动） ====================

    private void requirePlatformAdmin() {
        if (!UserUtils.isPlatformAdmin()) {
            throw new BusinessException("仅平台管理员可进行用户管理操作");
        }
    }

    /**
     * 平台账号列表（全部账号，含平台管理员标记；按创建时间倒序）
     */
    @GetMapping("/platform-admin/list")
    public R<List<AccountVO>> platformList(@RequestParam(value = "keyword", required = false) String keyword) {
        requirePlatformAdmin();
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Account>()
                .orderByDesc(Account::getCreatedAt);
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Account::getUsername, kw)
                    .or().like(Account::getNickname, kw)
                    .or().like(Account::getEmail, kw));
        }
        return R.data(BeanUtils.copyList(accountService.list(wrapper), AccountVO.class));
    }

    /**
     * 创建账号（不强制邮箱与租户归属；可直接授予平台管理员身份）
     */
    @PostMapping("/platform-admin")
    public R<Boolean> platformCreate(@RequestBody PlatformAccountCreateRequest request) {
        requirePlatformAdmin();
        return R.data(accountService.createPlatformAccount(request));
    }

    /**
     * 禁用/启用账号（内置 admin 账号不可操作）
     */
    @PutMapping("/platform-admin/{id}/toggle-enabled")
    public R<Boolean> platformToggleEnabled(@PathVariable("id") Long id, @RequestParam("enabled") Boolean enabled) {
        requirePlatformAdmin();
        return R.data(accountService.toggleEnabled(id, enabled));
    }

    /**
     * 重置密码（传明文，服务端以账号 ID 为盐加密存储）
     */
    @PutMapping("/platform-admin/{id}/reset-password")
    public R<Boolean> platformResetPassword(@PathVariable("id") Long id, @RequestParam("newPassword") String newPassword) {
        requirePlatformAdmin();
        return R.data(accountService.adminChangePassword(id, newPassword));
    }

    /**
     * 设置/取消平台管理员身份（内置 admin 账号不可取消）
     */
    @PutMapping("/platform-admin/{id}/platform-admin")
    public R<Boolean> platformSetAdmin(@PathVariable("id") Long id, @RequestParam("flag") Boolean flag) {
        requirePlatformAdmin();
        return R.data(accountService.setPlatformAdmin(id, flag));
    }

    /**
     * 删除账号（内置 admin 账号不可删除）
     */
    @DeleteMapping("/platform-admin")
    public R<Boolean> platformDelete(@RequestBody List<Long> ids) {
        requirePlatformAdmin();
        for (Account account : accountService.listByIds(ids)) {
            if (Objects.equals(account.getId(), SysConst.ADMIN_ACCOUNT_ID)) {
                throw new BusinessException("管理员账号不可删除");
            }
        }
        return R.data(accountService.removeByIds(ids));
    }
}
