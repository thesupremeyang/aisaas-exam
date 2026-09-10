package com.hxh.apboa.common.dto;

import lombok.Data;

/** 考生在开考前保存的专属模型配置。 */
@Data
public class ExamCandidateModelConfigRequest {
    private String providerKey;
    private String schemeId;
    private String modelId;
    private String apiKey;
    /** 仅自定义 OpenAI 兼容服务需要填写。 */
    private String baseUrl;
}
