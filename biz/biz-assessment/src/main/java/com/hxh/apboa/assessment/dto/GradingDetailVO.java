package com.hxh.apboa.assessment.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** AI 阅卷单卷详情：分项明细 + 主导权判定 + 理由 + 异常标记。 */
@Getter
@Setter
public class GradingDetailVO {
    private Long submissionId;
    private Long questionId;
    private Integer questionNo;
    private String questionTitle;
    private Integer questionScore;
    private Long enrollmentId;
    private String candidateName;
    private String loginName;
    private Long agentId;
    private String agentName;
    private String submissionStatus;
    private String taskStatus;
    private String taskErrorMessage;

    private BigDecimal baseScore;
    private BigDecimal coefficient;
    private Integer checklistHits;
    private List<String> checklistDetail;
    private String dominanceStatus;
    private List<Map<String, String>> dominanceChannels;
    /** 分项评分明细：name/score/max/evidence */
    private List<Map<String, Object>> items;
    private String capStatus;
    private BigDecimal capRatio;
    private BigDecimal deductionAmount;
    private String deductionReason;
    private BigDecimal totalScore;
    private BigDecimal configMaxScore;
    private String confidence;
    /** 通俗评语（面向非技术读者，页面主展示） */
    private String plainComment;
    /** 技术版评分摘要（管理员核查用） */
    private String summary;
    private List<String> integrityFlags;
    private String modelName;
    private String rubricVersion;
    private LocalDateTime gradedAt;
}
