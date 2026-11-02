package com.hxh.apboa.assessment.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/** AI 阅卷总览：进度统计 + 按考生分组的评分行。 */
@Getter
@Setter
public class GradingOverviewVO {
    private Long campaignId;
    private String campaignName;
    /** QUEUED / RUNNING / SUCCEEDED / FAILED 计数 */
    private Map<String, Long> taskCounts;
    private List<GradingCandidateRowVO> rows;
}
