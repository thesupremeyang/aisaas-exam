package com.hxh.apboa.assessment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 初评主实现：调用租户配置的大模型（火山方舟 Ark）基于考生提交材料进行真实 AI 评分。
 * 不启用大模型时直接抛错，绝不使用固定分兜底（避免"假成功/模拟演示"）。
 */
@Component
@Primary
public class RuntimeInitialAssessmentClient implements InitialAssessmentClient {
    private final ArkInitialAssessmentClient arkClient;
    private final RuntimeAssessmentProperties properties;

    public RuntimeInitialAssessmentClient(ArkInitialAssessmentClient arkClient,
                                          RuntimeAssessmentProperties properties) {
        this.arkClient = arkClient;
        this.properties = properties;
    }

    @Override
    public InitialAssessmentResult assess(String submissionMaterial) {
        return assess(submissionMaterial, AssessmentRubric.fromCampaignConfig(null));
    }

    @Override
    public InitialAssessmentResult assess(String submissionMaterial, AssessmentRubric rubric) {
        if (!properties.enabled()) {
            throw new IllegalStateException(
                    "初评大模型（Ark）未启用（assessment.runtime.enabled=false），无法进行真实 AI 评分");
        }
        // 直接调用真实 AI 评测；任何调用或校验失败均向上抛出，由任务调度标记为 FAILED，
        // 不再静默降级到固定分（防止"假成功"）。
        return arkClient.assess(submissionMaterial, rubric);
    }
}
