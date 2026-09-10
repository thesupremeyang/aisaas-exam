package com.hxh.apboa.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.consts.TableConst;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter @Setter @TableName(TableConst.EXAM_CAMPAIGN)
public class ExamCampaign extends BaseTenantEntity {
    private String name;
    private LocalDateTime registrationStart;
    private LocalDateTime registrationEnd;
    private LocalDateTime submissionStart;
    private LocalDateTime submissionEnd;
    private LocalDateTime reviewEnd;
    private String status;
    private String rubricVersion;
    private String configJson;

    /** 由 config_json 派生，仅用于接口展示，不落库。 */
    @TableField(exist = false)
    private Boolean scoreQueryEnabled;
}
