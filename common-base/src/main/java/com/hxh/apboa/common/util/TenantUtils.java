package com.hxh.apboa.common.util;

/**
 * 租户上下文工具类
 * 基于 ThreadLocal 存储当前请求的租户上下文
 *
 * @author huxuehao
 */
public class TenantUtils {

    private static final ThreadLocal<Long> CURRENT_TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_TENANT_CODE = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> IGNORE_TENANT = new ThreadLocal<>();

    public static Long getCurrentTenantId() {
        return CURRENT_TENANT_ID.get();
    }

    public static String getCurrentTenantCode() {
        return CURRENT_TENANT_CODE.get();
    }

    public static void setCurrentTenant(Long tenantId, String tenantCode) {
        CURRENT_TENANT_ID.set(tenantId);
        CURRENT_TENANT_CODE.set(tenantCode);
    }

    /**
     * 标记为忽略租户过滤（用于定时任务/后台线程跨租户扫描）。
     */
    public static void setIgnoreTenant() {
        IGNORE_TENANT.set(Boolean.TRUE);
    }

    public static boolean isIgnoreTenant() {
        return Boolean.TRUE.equals(IGNORE_TENANT.get());
    }

    public static void clearIgnoreTenant() {
        IGNORE_TENANT.remove();
    }

    public static void clear() {
        CURRENT_TENANT_ID.remove();
        CURRENT_TENANT_CODE.remove();
        IGNORE_TENANT.remove();
    }
}
