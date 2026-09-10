package com.hxh.apboa.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.consts.TableConst;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter @Setter @TableName(TableConst.ASSESSMENT_TASK)
public class AssessmentTask extends BaseTenantEntity {
    private Long submissionId;
    private String taskType;
    private String status;
    private Integer progress;
    private Integer attempt;
    private String inputHash;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
