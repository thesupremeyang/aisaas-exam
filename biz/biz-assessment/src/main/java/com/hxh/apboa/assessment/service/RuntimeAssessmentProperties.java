package com.hxh.apboa.assessment.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public record RuntimeAssessmentProperties(
        boolean enabled, String endpoint, String agentId, String model,
        String promptVersion, String systemPrompt, Duration timeout, String bearerToken) {

    public RuntimeAssessmentProperties(
            @Value("${assessment.runtime.enabled:false}") boolean enabled,
            @Value("${assessment.runtime.endpoint:http://127.0.0.1:3061/runtime/assessment/initial}") String endpoint,
            @Value("${assessment.runtime.agent-id:assessment-agent}") String agentId,
            @Value("${assessment.runtime.model:}") String model,
            @Value("${assessment.runtime.prompt-version:assessment-v1}") String promptVersion,
            @Value("${assessment.runtime.system-prompt:}") String systemPrompt,
            @Value("${assessment.runtime.timeout:30s}") Duration timeout,
            @Value("${assessment.runtime.bearer-token:}") String bearerToken) {
        this.enabled = enabled;
        this.endpoint = endpoint;
        this.agentId = agentId;
        this.model = model;
        this.promptVersion = promptVersion;
        this.systemPrompt = systemPrompt;
        this.timeout = timeout;
        this.bearerToken = bearerToken;
    }
}
