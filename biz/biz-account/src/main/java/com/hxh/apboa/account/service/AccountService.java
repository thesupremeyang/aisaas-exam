package com.hxh.apboa.account.service;

import com.hxh.apboa.common.dto.*;
import com.hxh.apboa.common.entity.Account;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 账号Service
 *
 * @author huxuehao
 */
public interface AccountService extends IService<Account> {

    /**
     * 用户登录
     *
     * @param request 登录请求
     * @return 登录响应
     */
    LoginResponse login(LoginRequest request);

    /**
     * 用户注册
     *
     * @param request 注册请求
     * @return 是否成功
     */
    boolean register(RegisterRequest request);

    /**
     * 刷新Token
     *
     * @param request 刷新请求
     * @return 登录响应
     */
    LoginResponse refreshToken(RefreshTokenRequest request);

    /**
     * 用户退出
     */
    void logout();

    /**
     * 修改密码
     *
     * @param request 修改密码请求
     * @return 是否成功
     */
    boolean changePassword(ChangePasswordRequest request);

    /**
     * 修改个人信息
     *
     * @param request 修改个人信息请求
     * @return 是否成功
     */
    boolean updateProfile(UpdateProfileRequest request);

    /**
     * 禁用/激活用户
     *
     * @param id 用户ID
     * @param enabled 是否启用
     * @return 是否成功
     */
    boolean toggleEnabled(Long id, Boolean enabled);

    /**
     * 管理员修改用户密码
     *
     * @param id 用户ID
     * @param newPassword 新密码
     * @return 是否成功
     */
    boolean adminChangePassword(Long id, String newPassword);

    /**
     * 通过ChatKey换取Token
     *
     * @param chatKey 对话Key
     * @return token，如果验证失败返回null
     */
    LoginResponse chatKeyToken(String chatKey);

    /**
     * 选择租户（多租户登录后选择进入的租户）
     *
     * @param request 租户选择请求
     * @return 登录响应（含租户上下文的 token）
     */
    LoginResponse selectTenant(SelectTenantRequest request);

    /**
     * 平台管理员创建账号（不强制邮箱与租户归属，可直接授予平台管理员身份）
     *
     * @param request 创建请求
     * @return 是否成功
     */
    boolean createPlatformAccount(PlatformAccountCreateRequest request);

    /**
     * 设置/取消平台管理员标记（内置 admin 账号不可取消）
     *
     * @param id   账号ID
     * @param flag 是否平台管理员
     * @return 是否成功
     */
    boolean setPlatformAdmin(Long id, Boolean flag);

    /**
     * 平台管理员进入考生租户查看：签发一个"模拟令牌"——管理员身份、考生租户上下文、
     * platformAdmin=false（不豁免跨租户）。模拟态下所有页面（智能体/Skill/知识库等）
     * 天然只显示该考生租户的内容；退出查看后换回管理员原令牌。
     *
     * @param tenantId 考生租户ID
     * @return 模拟登录响应（accessToken/userDetail）
     */
    LoginResponse viewTenantAsAdmin(Long tenantId);
}
