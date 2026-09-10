package com.hxh.apboa.assessment.service;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

/**
 * Validates the portal location to which an SSO hand-off may return.
 */
public final class ExamSsoReturnUrlPolicy {
    private final Set<String> allowedOrigins;

    public ExamSsoReturnUrlPolicy(Set<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(this::configuredOrigin)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public void validate(String returnUrl) {
        if (returnUrl == null || returnUrl.isBlank()) {
            throw new IllegalArgumentException("返回地址不能为空");
        }
        URI uri;
        try {
            uri = URI.create(returnUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("返回地址格式无效", exception);
        }
        if (!uri.isAbsolute()) {
            if (!returnUrl.startsWith("/") || returnUrl.startsWith("//") || returnUrl.contains("\\")) {
                throw new IllegalArgumentException("返回地址不在白名单内");
            }
            return;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                || uri.getFragment() != null || !allowedOrigins.contains(originOf(uri))) {
            throw new IllegalArgumentException("返回地址不在白名单内");
        }
    }

    private String configuredOrigin(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath())
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("返回地址白名单配置无效");
            }
            return originOf(uri);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("返回地址白名单配置无效", exception);
        }
    }

    private String originOf(URI uri) {
        if (!uri.isAbsolute() || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("返回地址白名单配置无效");
        }
        var port = uri.getPort();
        return "https://" + uri.getHost().toLowerCase() + (port == -1 || port == 443 ? "" : ":" + port);
    }
}
