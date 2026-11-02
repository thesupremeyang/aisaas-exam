package com.hxh.apboa.assessment.dto;

import lombok.Getter;
import lombok.Setter;

/** 场次级成绩发布结果：把复核分固化为报名记录的实操分，并开启查分。 */
@Getter
@Setter
public class ExamScorePublishVO {
    private Long campaignId;
    /** 本场报名总数。 */
    private int enrollmentCount;
    /** 成功写入实操分的考生数。 */
    private int publishedCount;
    /** 没有任何有效提交、无法计算实操分的考生数。 */
    private int skippedCount;
    /** 仍有作品未复核的考生数（发布前用于提示，发布后仍可能大于 0）。 */
    private int pendingReviewCount;
    /** 查分开关是否已开启。 */
    private boolean scoreQueryEnabled;
}
