package com.hxh.apboa.common.dto;

import com.hxh.apboa.common.enums.AuthType;
import com.hxh.apboa.common.enums.ModelProviderType;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 直接使用表单参数检测模型连接，不落库。 */
@Data
@NoArgsConstructor
public class ModelConnectionCheckRequest {
    private ModelProviderType type;
    private String baseUrl;
    private String modelId;
    private AuthType authType = AuthType.CONFIG;
    private String apiKey;
    private String envVarName;
}
