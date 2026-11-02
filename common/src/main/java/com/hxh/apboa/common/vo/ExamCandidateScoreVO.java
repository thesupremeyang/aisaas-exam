package com.hxh.apboa.common.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/** 考生查分模式下可见的本人考试成绩。 */
@Getter
@Setter
public class ExamCandidateScoreVO {
    private Long campaignId;
    private String campaignName;
    private BigDecimal totalScore;
    private Integer theoryScore;
    private Integer practicalScore;
    private Integer totalScoreMax = 100;
    private Integer theoryScoreMax = 100;
    private Integer practicalScoreMax = 100;
    /** 理论分是否已录入；未录入时总成绩为空，考生端据此提示「成绩评定中」。 */
    private boolean theoryScoreEntered;
    /** 实操分是否已经由管理员发布固化。 */
    private boolean practicalScorePublished;
    private List<QuestionScore> questions = List.of();

    @Getter
    @Setter
    public static class QuestionScore {
        private Integer questionNo;
        private Long questionId;
        private String questionTitle;
        private Integer questionScore;
        private Long submissionId;
        private Long agentId;
        private String agentName;
        /** 本题复核最终得分（已复核时非空）。 */
        private Integer finalScore;
        /** 复核意见；未复核为空。 */
        private String reviewComment;
    }
}
