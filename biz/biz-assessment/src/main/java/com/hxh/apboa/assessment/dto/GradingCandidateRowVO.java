package com.hxh.apboa.assessment.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/** AI 阅卷列表中按考生聚合的行。 */
@Getter
@Setter
public class GradingCandidateRowVO {
    private Long enrollmentId;
    private String candidateName;
    private String loginName;
    private String organization;
    private String tenantName;
    private Integer questionCount;
    private Integer submittedCount;
    private List<GradingQuestionCellVO> questions;
    /** 已评分题目阅卷分之和 */
    private BigDecimal totalScore;
    /** 考试满分（各题分值之和） */
    private BigDecimal totalMaxScore;
}
