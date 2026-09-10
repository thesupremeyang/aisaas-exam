package com.hxh.apboa.console.account;

import com.hxh.apboa.account.service.AccountService;
import com.hxh.apboa.common.config.auth.ChatKeyAccess;
import com.hxh.apboa.common.config.auth.RoleNeed;
import com.hxh.apboa.common.config.auth.SkAccess;
import com.hxh.apboa.common.consts.SysConst;
import com.hxh.apboa.common.dto.AccountDTO;
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
}
