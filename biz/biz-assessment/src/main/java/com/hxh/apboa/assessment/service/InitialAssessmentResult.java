package com.hxh.apboa.assessment.service;

import java.util.Map;

/** 初评结构化结果：各维度得分、总分、置信度与文字结论。 */
public record InitialAssessmentResult(
        Map<String, Integer> dimensionScores, int totalScore, String confidence, String conclusion, String evidence) {

    public int computeFinalScore() {
        return dimensionScores.values().stream().mapToInt(Integer::intValue).sum();
    }

    public String brief() {
        return conclusion + "（置信度 " + confidence + "，总分 " + totalScore + "）";
    }
}
