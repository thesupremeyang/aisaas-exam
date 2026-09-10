package com.hxh.apboa.common.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 返回给考生的模型配置摘要，绝不回传 API Key。 */
@Data
public class ExamCandidateModelConfigVO {
    private Long configId;
    private String providerKey;
    private String schemeId;
    private String providerName;
    private String modelId;
    private String baseUrl;
    private boolean apiKeyConfigured;
    private String connectivityStatus;
    private String connectivityMessage;
    private LocalDateTime lastConnectivityCheck;
}
