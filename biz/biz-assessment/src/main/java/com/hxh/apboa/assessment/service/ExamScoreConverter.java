package com.hxh.apboa.assessment.service;

/** 将评分量表原始分折算为考试题目分值。 */
public final class ExamScoreConverter {
    private ExamScoreConverter() {
    }

    public static int toQuestionScore(Integer rawScore, int rawScoreLimit, int questionScore) {
        if (rawScore == null) return 0;
        if (rawScoreLimit <= 0 || questionScore <= 0) return rawScore;
        int boundedScore = Math.max(0, Math.min(rawScore, rawScoreLimit));
        return (int) Math.round((double) boundedScore * questionScore / rawScoreLimit);
    }
}
