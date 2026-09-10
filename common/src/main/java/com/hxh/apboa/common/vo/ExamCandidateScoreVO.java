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
    }
}
