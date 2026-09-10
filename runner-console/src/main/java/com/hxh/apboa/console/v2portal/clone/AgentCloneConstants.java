package com.hxh.apboa.console.v2portal.clone;

/**
 * 方案 G 相关常量
 *
 * @author agent-clone-feature
 */
public final class AgentCloneConstants {
    private AgentCloneConstants() {}

    /** 源 agent 已禁用 */
    public static final String ERR_SOURCE_DISABLED = "源智能体已禁用，无法克隆";
    /** 源 agent 不存在 */
    public static final String ERR_SOURCE_NOT_FOUND = "源智能体不存在";
    /** caller 租户未识别 */
    public static final String ERR_NO_CALLER_TENANT = "无法识别当前用户租户";

    /** 克隆副本名称后缀 */
    public static final String COPY_NAME_SUFFIX = " (副本)";
    /** 克隆副本 agentCode 固定分隔符（前缀用源 code + 分隔符 + uuid8） */
    public static final String COPY_CODE_SEPARATOR = "-copy-";
    /** UUID 截断长度，避免 agentCode 过长 */
    public static final int UUID_SUFFIX_LENGTH = 8;
}
