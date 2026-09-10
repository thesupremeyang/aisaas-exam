package com.hxh.apboa.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.consts.TableConst;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName(TableConst.ASSESSMENT_REVIEW)
public class AssessmentReview extends BaseTenantEntity {
    private Long submissionId;
    private Long reviewerAccountId;
    private String scoresJson;
    private Integer totalScore;
    private String comment;
    private String checklistJson;
    private String status;
}
