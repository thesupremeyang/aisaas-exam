package com.hxh.apboa.assessment.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端「答卷管理」列表行 VO。
 *
 * @author workbuddy
 */
@Data
public class ExamSubmissionAdminVO {
    private Long submissionId;
    private Long enrollmentId;
    /** 考生所属租户 ID（管理员进入考生智能体配置界面用）。 */
    private Long tenantId;
    /** 考生姓名（取考生账号昵称，回退报名表姓名）。 */
    private String candidateName;
    /** 考号（考生登录账号）。 */
    private String candidateUsername;
    /** 考生组织/班级。 */
    private String organization;
    private Long questionId;
    private String questionTitle;
    private Integer questionScore;
    /** 答卷关联的考生智能体，可能为空（未创建作品或已删除）。 */
    private Long agentId;
    private String agentName;
    /** 作品类型（考生提交方式）。 */
    private String workType;
    /** 答卷状态（DRAFT / AI_QUEUED / REVIEW_PENDING / PUBLISHED / TIMEOUT_* 等）。 */
    private String status;
    /** 考生答题步骤。 */
    private Integer currentStep;
    private LocalDateTime submittedAt;
}
