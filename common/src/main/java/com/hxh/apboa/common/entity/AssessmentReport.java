package com.hxh.apboa.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.consts.TableConst;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName(TableConst.ASSESSMENT_REPORT)
public class AssessmentReport extends BaseTenantEntity {
    private Long submissionId;
    private Long taskId;
    private String rubricVersion;
    private String promptVersion;
    private String modelName;
    private String reportJson;
    private Integer totalScore;
    private String confidence;
}
