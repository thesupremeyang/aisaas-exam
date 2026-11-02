package com.hxh.apboa.common.dto;

import lombok.Data;

/**
 * 平台管理员创建账号请求（用户管理页专用；与自助注册不同：
 * 不强制邮箱、不强制租户归属，可直接授予平台管理员身份）
 *
 * @author workbuddy
 */
@Data
public class PlatformAccountCreateRequest {
    /** 登录用户名（唯一） */
    private String username;
    /** 昵称 */
    private String nickname;
    /** 登录密码（明文，服务端加盐 MD5 存储） */
    private String password;
    /** 邮箱（可选） */
    private String email;
    /** 是否平台管理员 */
    private Boolean platformAdmin;
}
