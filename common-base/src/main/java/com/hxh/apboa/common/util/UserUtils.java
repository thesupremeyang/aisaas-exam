package com.hxh.apboa.common.util;

import com.hxh.apboa.common.UserDetail;
import com.hxh.apboa.common.consts.SysConst;
import com.hxh.apboa.common.enums.TenantRole;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 描述：用户工具类
 *
 * @author huxuehao
 **/
public class UserUtils {
    private static final String PLATFORM_ADMIN_USERNAMES_ENV = "APBOA_PLATFORM_ADMIN_USERNAMES";

    public static Long getId() {
        UserDetail userDetail = getUserDetail();
        if (userDetail == null) {
            return 0L;
        }
        return userDetail.getId();
    }

    public static String getAccount() {
        UserDetail userDetail = getUserDetail();
        if (userDetail == null) {
            return null;
        }
        return userDetail.getUsername();
    }

    /**
     * 判断当前登录用户是否为平台管理员。
     *
     * <p>优先读取账号表的平台管理员标记（随登录 token 快照生效），
     * 同时保留环境变量/系统属性配置的平台管理员账号作为兜底（默认 admin）。</p>
     */
    public static boolean isPlatformAdmin() {
        UserDetail userDetail = getUserDetail();
        if (userDetail != null && Boolean.TRUE.equals(userDetail.getPlatformAdmin())) {
            return true;
        }
        return isPlatformAdminUsername(getAccount());
    }

    public static boolean isPlatformAdminUsername(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        String configured = System.getProperty("apboa.platform-admin-usernames");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(PLATFORM_ADMIN_USERNAMES_ENV);
        }
        if (configured == null || configured.isBlank()) {
            configured = "admin";
        }
        for (String item : configured.split(",")) {
            if (username.trim().equalsIgnoreCase(item.trim())) {
                return true;
            }
        }
        return false;
    }

    public static Long getTenantId() {
        UserDetail userDetail = getUserDetail();
        return userDetail != null ? userDetail.getTenantId() : null;
    }

    public static String getTenantCode() {
        UserDetail userDetail = getUserDetail();
        return userDetail != null ? userDetail.getTenantCode() : null;
    }

    public static UserDetail getUserDetail() {
        try {
            HttpServletRequest request = WebUtils.getRequest();
            if (request == null) {
                return null;
            }
            return (UserDetail) request.getAttribute(SysConst.USER_DETAIL);
        } catch (IllegalStateException e) {
            // 异步线程或请求生命周期结束后，request 可能已回收，此时按匿名上下文处理。
            return null;
        }
    }

    /**
     * 判断当前登录用户是否为租户管理员（OWNER 视为管理员）
     */
    public static boolean isAdmin() {
        UserDetail userDetail = getUserDetail();
        if (userDetail == null || userDetail.getTenantRole() == null) {
            return false;
        }
        return TenantRole.TENANT_ADMIN.name().equals(userDetail.getTenantRole())
                || TenantRole.TENANT_OWNER.name().equals(userDetail.getTenantRole());
    }
}
