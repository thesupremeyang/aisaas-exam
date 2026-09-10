package com.hxh.apboa.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.consts.TableConst;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter @Setter @TableName(TableConst.EXAM_SUBMISSION)
public class ExamSubmission extends BaseTenantEntity {
    private Long enrollmentId;
    private Long questionId;
    private Long agentId;
    private String workType;
    private String formJson;
    private Integer currentStep;
    private String status;
    private Integer version;
    private LocalDateTime submittedAt;

    /** 仅用于考生页面展示，不落库。 */
    @TableField(exist = false)
    private String agentName;

    /** 以下字段仅用于向考生返回当前考试时间，不落库。 */
    @TableField(exist = false)
    private String examState;
    @TableField(exist = false)
    private LocalDateTime examStartAt;
    @TableField(exist = false)
    private LocalDateTime examEndAt;
    @TableField(exist = false)
    private LocalDateTime serverNow;
}
