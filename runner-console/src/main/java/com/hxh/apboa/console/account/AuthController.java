package com.hxh.apboa.console.account;

import com.hxh.apboa.account.service.AccountService;
import com.hxh.apboa.common.config.auth.PassAuth;
import com.hxh.apboa.common.config.auth.RoleNeed;
import com.hxh.apboa.common.dto.*;
import com.hxh.apboa.common.enums.TenantRole;
import com.hxh.apboa.common.r.R;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

/**
 * 认证Controller
 *
 * @author huxuehao
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AccountService accountService;

    /** 自助注册开关：考试场景默认关闭，账号由管理员统一开通 */
    @Value("${auth.register.enabled:false}")
    private boolean registerEnabled;

    /**
     * 用户登录
     */
    @PassAuth
    @PostMapping("/login")
    public R<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResponse res = accountService.login(request);
        if (Boolean.TRUE.equals(res.getBlocked())) {
            return R.data(res, "您的加入申请正在审批中");
        }
        return R.data(res, "登录成功");
    }

    /**
     * 用户注册
     */
    @PassAuth
    @PostMapping("/register")
    public R<Boolean> register(@RequestBody RegisterRequest request) {
        if (!registerEnabled) {
            return R.fail("自助注册已关闭，账号由考试管理员统一开通");
        }
        boolean result = accountService.register(request);
        return R.data(result);
    }

    /**
     * 刷新Token
     */
    @PassAuth
    @PostMapping("/refresh-token")
    public R<LoginResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
        LoginResponse res = accountService.refreshToken(request);
        return R.data(res, "刷新成功");
    }

    /**
     * 用户退出
     */
    @PostMapping("/logout")
    public R<Void> logout() {
        accountService.logout();
        return R.success("退出成功");
    }

    /**
     * 刷新当前页面的在线状态。在线标记由认证拦截器按短 TTL 自动续期。
     */
    @PostMapping("/heartbeat")
    public R<Void> heartbeat() {
        return R.success("在线状态已刷新");
    }

    /**
     * 修改密码
     */
    @PostMapping("/change-password")
    public R<Boolean> changePassword(@RequestBody ChangePasswordRequest request) {
        boolean result = accountService.changePassword(request);
        return R.data(result);
    }

    /**
     * 修改个人信息
     */
    @PostMapping("/update-profile")
    public R<Boolean> updateProfile(@RequestBody UpdateProfileRequest request) {
        boolean result = accountService.updateProfile(request);
        return R.data(result);
    }

    /**
     * 管理员新增用户
     */
    @RoleNeed({TenantRole.TENANT_ADMIN})
    @PostMapping("/admin/create-account")
    public R<Boolean> adminCreateAccount(@RequestBody RegisterRequest request) {
        return R.data(accountService.register(request));
    }

    /**
     * 通过ChatKey换取Token
     */
    @PassAuth
    @PostMapping("/chat-key-token/{chatKey}")
    public R<LoginResponse> chatKeyToken(@PathVariable("chatKey") String chatKey) {
        return R.data(accountService.chatKeyToken(chatKey));
    }

    /**
     * 选择租户（多租户登录后选择进入的租户）
     */
    @PostMapping("/select-tenant")
    public R<LoginResponse> selectTenant(@RequestBody SelectTenantRequest request) {
        LoginResponse res = accountService.selectTenant(request);
        return R.data(res, "租户选择成功");
    }

    /**
     * 平台管理员进入考生租户查看（签发模拟令牌：考生租户上下文、无平台豁免；
     * 退出查看时前端换回管理员原令牌）
     */
    @PostMapping("/admin/view-tenant")
    public R<LoginResponse> adminViewTenant(@RequestBody SelectTenantRequest request) {
        return R.data(accountService.viewTenantAsAdmin(request.getTenantId()), "已进入考生配置界面");
    }
}
