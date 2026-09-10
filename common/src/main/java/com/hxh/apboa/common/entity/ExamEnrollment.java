package com.hxh.apboa.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.consts.TableConst;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter @TableName(TableConst.EXAM_ENROLLMENT)
public class ExamEnrollment extends BaseTenantEntity {
    private Long campaignId;
    private Long accountId;
    private String name;
    private String phoneCiphertext;
    private String idNoCiphertext;
    private String organization;
    private String status;
    /** 理论考试原始分（0-100），查分模式使用。 */
    private Integer theoryScore;
    /** 实操考试原始分（0-100），查分模式使用。 */
    private Integer practicalScore;
    /** 考生首次进入开放考试窗口的时间。 */
    private LocalDateTime enteredAt;
    /** 本场考试中考生专属的模型配置。 */
    private Long modelConfigId;
}
