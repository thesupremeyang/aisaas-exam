package com.hxh.apboa.assessment.service;

import java.util.Map;
import java.util.Objects;

/** Ensures dimension scores agree with the active campaign rubric and total. */
public final class AssessmentScoreValidator {
    public static final Map<String, Integer> RUBRIC_MAX_SCORES = Map.of(
            "SCENE_VALUE", 20,
            "FUNCTION_COMPLETENESS", 20,
            "INNOVATION", 20,
            "TECHNICAL_DESCRIPTION", 20,
            "VERIFIABILITY", 20);

    private AssessmentScoreValidator() {
    }

    public static void validate(Map<String, Integer> dimensionScores, Integer totalScore) {
        validate(dimensionScores, totalScore,
                new AssessmentRubric(RUBRIC_MAX_SCORES, Map.of("EXCELLENT", 90, "GOOD", 75, "PASS", 60), 3));
    }

    public static void validate(Map<String, Integer> dimensionScores, Integer totalScore,
                                AssessmentRubric rubric) {
        var maxScores = rubric.maxScores();
        if (dimensionScores == null || !dimensionScores.keySet().equals(maxScores.keySet())) {
            throw new IllegalArgumentException("评分项必须完整并符合当前评分量表");
        }
        if (totalScore == null || totalScore < 0 || totalScore > rubric.totalScore()) {
            throw new IllegalArgumentException("总分超出当前评分量表范围");
        }

        int calculatedTotal = 0;
        for (var entry : maxScores.entrySet()) {
            var score = dimensionScores.get(entry.getKey());
            if (score == null || score < 0 || score > entry.getValue()) {
                throw new IllegalArgumentException(entry.getKey() + " 的分数超出当前评分量表范围");
            }
            calculatedTotal += score;
        }
        if (!Objects.equals(calculatedTotal, totalScore)) {
            throw new IllegalArgumentException("总分必须等于各评分项之和");
        }
    }
}
