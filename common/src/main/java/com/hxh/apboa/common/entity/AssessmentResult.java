package com.hxh.apboa.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.consts.TableConst;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter @Setter @TableName(TableConst.ASSESSMENT_RESULT)
public class AssessmentResult extends BaseTenantEntity {
    private Long submissionId;
    private Integer finalScore;
    private String level;
    private Long publishedBy;
    private LocalDateTime publishedAt;
    private Integer version;
}
