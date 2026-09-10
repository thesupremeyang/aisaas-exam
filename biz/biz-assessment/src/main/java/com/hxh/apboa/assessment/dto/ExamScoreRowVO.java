package com.hxh.apboa.assessment.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 评审端学员 AI 自评分数列表行 */
@Getter
@Setter
public class ExamScoreRowVO {
    /** 一次考试报名，对应一名学员在一个考试批次中的全部试题。 */
    private Long enrollmentId;
    private Long submissionId;
    private String candidateName;
    private String identityNumber;
    private String phone;
    private String loginName;
    private String organization;
    private String tenantName;
    private String workType;
    private String workTypeLabel;
    private String workName;
    private String demoUrl;
    private String submissionStatus;
    private LocalDateTime submittedAt;
    private Integer questionCount;
    private Integer submittedQuestionCount;
    private Integer aiScoredQuestionCount;
    private Integer reviewedQuestionCount;
    private Integer demoCount;
    // AI 初评
    private Integer aiTotalScore;
    private Integer totalLimit;
    private String confidence;
    private String modelName;
    private LocalDateTime aiScoredAt;
    // 管理员复核
    private Integer finalScore;
    private String reviewComment;
    private boolean reviewed;
}
