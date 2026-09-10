package com.hxh.apboa.assessment.service;

/** 默认 100 分制成绩等级；后续可由考试批次配置覆盖。 */
public final class FinalScoreLevel {
    private static final int DEFAULT_TOTAL_SCORE = 100;

    private FinalScoreLevel() {
    }

    public static String of(int score) {
        if (score < 0 || score > DEFAULT_TOTAL_SCORE) {
            throw new IllegalArgumentException("最终得分必须在 0 到 100 之间");
        }
        if (score >= 90) return "EXCELLENT";
        if (score >= 75) return "GOOD";
        if (score >= 60) return "PASS";
        return "FAIL";
    }
}
