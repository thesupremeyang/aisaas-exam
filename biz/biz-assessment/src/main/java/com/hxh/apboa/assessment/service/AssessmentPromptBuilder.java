package com.hxh.apboa.assessment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;

/** Builds the model-visible assessment context from the active rubric and submitted form. */
public class AssessmentPromptBuilder {
    private final ObjectMapper jsonMapper;

    public AssessmentPromptBuilder(ObjectMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public String build(String submissionMaterial, AssessmentRubric rubric) {
        try {
            var payload = new LinkedHashMap<String, Object>();
            payload.put("rubric", Map.of("dimensions", rubric.maxScores(), "levels", rubric.levels()));
            payload.put("submission", sanitizeSubmission(submissionMaterial));
            payload.put("expectedResponse", Map.of(
                    "dimensionScores", "按 rubric.dimensions 的维度编码返回整数得分",
                    "totalScore", "各维度得分之和",
                    "confidence", "HIGH、MEDIUM 或 LOW",
                    "conclusion", "简洁中文初评结论",
                    "evidence", "数组，每项包含 dimension 和 content，列出实际评分依据",
                    "risks", "字符串数组，列出具体风险点；无风险则返回空数组",
                    "suggestions", "字符串数组，列出可执行的改进建议；无建议则返回空数组"));
            return jsonMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new RuntimeAssessmentException(RuntimeAssessmentException.Kind.INVALID_RESPONSE,
                    "Unable to build assessment prompt", ex);
        }
    }

    private JsonNode sanitizeSubmission(String submissionMaterial) {
        try {
            JsonNode root = jsonMapper.readTree(submissionMaterial == null ? "{}" : submissionMaterial);
            if (root instanceof ObjectNode objectNode && objectNode.path("applicant") instanceof ObjectNode applicant) {
                applicant.remove("identityNumber");
                applicant.remove("phone");
            }
            return root;
        } catch (Exception ignored) {
            return jsonMapper.getNodeFactory().textNode("");
        }
    }
}
