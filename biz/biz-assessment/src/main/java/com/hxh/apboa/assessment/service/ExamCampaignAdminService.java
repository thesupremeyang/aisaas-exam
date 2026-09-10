package com.hxh.apboa.assessment.service;

import com.hxh.apboa.common.dto.ExamCampaignScheduleRequest;
import com.hxh.apboa.common.dto.ExamCampaignUpdateRequest;
import com.hxh.apboa.common.entity.ExamCampaign;

import java.util.List;

/** 平台管理员考试批次排期服务。 */
public interface ExamCampaignAdminService {
    List<ExamCampaign> listCampaigns();

    ExamCampaign createCampaign(ExamCampaignUpdateRequest request);

    ExamCampaign updateCampaign(Long campaignId, ExamCampaignUpdateRequest request);

    ExamCampaign updateEnabled(Long campaignId, Boolean enabled);

    ExamCampaign updateScoreQueryEnabled(Long campaignId, Boolean enabled);

    ExamCampaign updateSchedule(Long campaignId, ExamCampaignScheduleRequest request);

    ExamCampaign startNow(Long campaignId);
}
