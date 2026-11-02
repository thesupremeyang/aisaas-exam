package com.hxh.apboa.assessment.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** AI 阅卷列表中单个题目的评分概要格。 */
@Getter
@Setter
public class GradingQuestionCellVO {
    private Long questionId;
    private Integer questionNo;
    private String questionTitle;
    /** 题目原始满分（exam_question.score） */
    private Integer questionScore;
    private Long submissionId;
    private Long agentId;
    private String agentName;
    private String submissionStatus;
    /** QUEUED / RUNNING / SUCCEEDED / FAILED / 未提交 / 无任务 */
    private String taskStatus;
    private String taskErrorMessage;
    private Boolean graded;
    private BigDecimal totalScore;
    private BigDecimal configMaxScore;
    private String dominanceStatus;
    private String capStatus;
    private String confidence;
    private String modelName;
    private List<String> integrityFlags;
    private LocalDateTime gradedAt;
}
