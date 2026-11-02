package com.hxh.apboa.assessment.service;

import com.hxh.apboa.assessment.dto.GradingDetailVO;
import com.hxh.apboa.assessment.dto.GradingOverviewVO;

import java.util.Map;

/** AI 阅卷（配置评分）管理端服务：入队、总览、详情、采纳为复核得分。 */
public interface ExamGradingAdminService {

    /** 按场次批量入队：返回 {enqueued, skippedRunning, withoutAgent}。已有 QUEUED/RUNNING 任务的答卷跳过。 */
    Map<String, Integer> startCampaignGrading(Long campaignId);

    /** 单卷重新入队（该卷无进行中任务时）。 */
    void rerunSubmission(Long submissionId);

    /** 场次阅卷总览：任务进度统计 + 按考生分组的逐题评分行。 */
    GradingOverviewVO overview(Long campaignId);

    /** 单卷阅卷详情（最新一次结果）。 */
    GradingDetailVO detail(Long submissionId);

    /** 把 AI 阅卷分采纳为复核得分（按配置分/配置满分折算到题目满分）。 */
    void adopt(Long submissionId, Long reviewerId);
}
