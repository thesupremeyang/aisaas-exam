package com.hxh.apboa.common.util;

import java.net.URI;

/**
 * 模型服务 Base URL 的统一校验。
 */
public final class ModelEndpointValidator {
    private ModelEndpointValidator() {
    }

    /**
     * 规范化并校验可用于模型 API 的 HTTP(S) 基础地址。
     */
    public static String normalizeHttpBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("请填写 Base URL");
        }

        String baseUrl = value.trim().replaceAll("/+$", "");
        final URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Base URL 格式错误，请填写完整的 HTTP(S) 地址", e);
        }

        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Base URL 仅支持 HTTP 或 HTTPS");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("Base URL 缺少主机名");
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("Base URL 不应包含查询参数或片段");
        }
        return baseUrl;
    }
}
