package com.hxh.apboa.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.consts.TableConst;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 阅卷模型配置（全局单行，id=1；表在租户拦截器忽略清单中，不做租户过滤）。
 * 运行时由 ExamGradingLlmClient 动态读取，修改后无需重启。
 */
@Getter
@Setter
@TableName(TableConst.AI_GRADING_SETTINGS)
public class AiGradingSettings extends BaseEntity {
    /** OpenAI 兼容 chat/completions 地址 */
    private String endpoint;
    /** API Key */
    private String apiKey;
    /** 模型名 */
    private String model;
    /** 是否启用 */
    private Boolean enabled;
}
