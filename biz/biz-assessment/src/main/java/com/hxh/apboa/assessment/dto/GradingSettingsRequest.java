package com.hxh.apboa.assessment.dto;

import lombok.Data;

/**
 * AI 阅卷模型配置保存/测试请求。
 * apiKey 为空表示保留现有 Key（修改 endpoint/model 时无需重复粘贴）。
 */
@Data
public class GradingSettingsRequest {
    private String endpoint;
    private String apiKey;
    private String model;
    private Boolean enabled;
}
