package com.hxh.apboa.common.consts;

import com.hxh.apboa.common.util.CryptoUtils;
import com.hxh.apboa.common.util.TenantUtils;

import java.util.List;

/**
 * 描述：系统常量
 *
 * @author huxuehao
 **/
public class SysConst {
    /**
     * JWT密钥
     */
    public static final String JWT_SECRET_KEY = "jwt.secret";
    /**
     * 登录用户Key
     */
    public static final String LOGIN_USER_KEY = "LOGIN-USER-KEY";
    public static final String USER_DETAIL = "USER-DETAIL";

    /**
     * token过期时间（6小时）
     */
    public static final Long ACCESS_TOKEN_TTL = 1000 * 60 * 60 * 6L;

    /**
     * 在线状态标记的有效期（心跳停止后自动离线）
     */
    public static final Long LOGIN_PRESENCE_TTL_MILLIS = 45_000L;

    /**
     * refresh token过期时间（12小时）
     */
    public static final Long REFRESH_TOKEN_TTL = 1000 * 60 * 60 * 18L;

    /**
     * 单个文件的最大体积（单位：MB）
     */
    public static final String SINGLE_FILE_MAX_SIZE = "5";

    public static final String ALLOW_IMAGE_FILE_TYPE = "png,jpeg,png,gif,webp";
    public static final String ALLOW_AUDIO_FILE_TYPE = "mp3,wav,mpeg";
    public static final String ALLOW_VIDEO_FILE_TYPE = "mp4,mpeg";
    public static final String ALLOW_DOC_FILE_TYPE = "doc,docx,pdf,txt,md";
    public static final String ALLOW_EXCEL_FILE_TYPE = "xlsx,xls,csv";
    public static final String ALLOW_PPT_FILE_TYPE = "pptx,ppt";

    public static final Long ADMIN_ACCOUNT_ID = 1111111111111111111L;

    public static final String CURRENT_NODE_ID = CryptoUtils.uuid();

    // 工作空间相关。考试版通过 JVM 参数或环境变量切换到独立目录，默认值保持兼容。
    public static final String ROOT_DIR_NAME = resolveRootDirName();
    public static final String TENANTS_DIR_NAME = "tenants";
    public static final String WORKSPACE_DIR_NAME = "workspaces";
    public static final String SKILLS_DIR_NAME = "skills";
    public static final double WORKSPACE_CAPACITY_MB = 30; // 工作空间容量限制（单位：MB）

    /**
     * 获取多租户隔离的 workspace 路径
     * 格式: .apboa/tenants/{tenantCode}/workspaces
     */
    public static String getWorkspacePath() {
        String tenantCode = TenantUtils.getCurrentTenantCode();
        return getWorkspacePath(tenantCode);
    }

    /**
     * 获取多租户隔离的 workspace 路径
     * 格式: .apboa/tenants/{tenantCode}/workspaces
     *
     * @param tenantCode 租户编号
     */
    public static String getWorkspacePath(String tenantCode) {
        if (tenantCode == null) {
            throw new RuntimeException("租户编号不可为空");
        }
        return ROOT_DIR_NAME + "/" + TENANTS_DIR_NAME + "/" + tenantCode + "/" + WORKSPACE_DIR_NAME;
    }

    /**
     * 获取多租户隔离的 skills 目录路径
     * 格式: .apboa/tenants/{tenantCode}/skills
     */
    public static String getSkillsDir() {
        String tenantCode = TenantUtils.getCurrentTenantCode();
        return getSkillsDir(tenantCode);
    }

    /**
     * 获取多租户隔离的 skills 目录路径
     * 格式: .apboa/tenants/{tenantCode}/skills
     *
     * @param tenantCode 租户编号
     */
    public static String getSkillsDir(String tenantCode) {
        if (tenantCode == null) {
            throw new RuntimeException("租户编号不可为空");
        }
        return ROOT_DIR_NAME + "/" + TENANTS_DIR_NAME + "/" + tenantCode + "/" + SKILLS_DIR_NAME;
    }

    /**
     * 获取租户根路径
     * 格式: .apboa/tenants/{tenantCode}
     */
    public static String getTenantPath(String tenantCode) {
        return ROOT_DIR_NAME + "/" + TENANTS_DIR_NAME + "/" + tenantCode;
    }

    // 工作空间钩子错误键
    public static final String WORKSPACE_HOOK_ERROR_KEY = "workspace_hook_error";

    // Skill Source
    public static final String SKILL_SOURCE = "apboa";

    private static String resolveRootDirName() {
        String configured = System.getProperty("apboa.root-dir");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("APBOA_ROOT_DIR");
        }
        return configured == null || configured.isBlank() ? ".apboa" : configured.trim();
    }
}
