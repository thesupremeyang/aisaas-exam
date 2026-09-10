package com.hxh.apboa.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.consts.TableConst;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName(TableConst.EXAM_QUESTION)
public class ExamQuestion extends BaseTenantEntity {
    private Long campaignId;
    private String title;
    private Integer score;
    private Integer suggestedMinutes;
    private String taskScenario;
    private String taskRequirement;
    private String submissionDeliverable;
    private String attachmentUrl;
    private String attachmentName;
    private Long attachmentSize;
    private Integer sortOrder;
}
