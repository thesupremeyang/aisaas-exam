package com.hxh.apboa.assessment.service;

import java.util.List;
import java.util.Map;

/** 初评模型适配边界；生产环境可替换为调用现有 AI Runtime 的实现。 */
public interface InitialAssessmentClient {
    InitialAssessmentResult assess(String submissionMaterial);

    default InitialAssessmentResult assess(String submissionMaterial, AssessmentRubric rubric) {
        return assess(submissionMaterial);
    }

    record AssessmentEvidence(String dimension, String content) {}

    record InitialAssessmentResult(Map<String, Integer> dimensionScores, Integer totalScore,
                                   String confidence, String conclusion, List<AssessmentEvidence> evidence,
                                   List<String> risks, List<String> suggestions) {
    }
}
