package com.hxh.apboa.assessment.service;

import com.hxh.apboa.assessment.dto.ExamSubmissionAdminVO;

import java.util.List;

/**
 * 平台 admin 专用答卷管理查询服务。
 *
 * @author workbuddy
 */
public interface ExamSubmissionAdminService {

    /**
     * 拉取一场考试的全部答卷（含考生、题目与智能体信息）。
     * 平台管理员跨租户读取，依赖 AuthInterceptor 的 ignore-tenant 豁免。
     */
    List<ExamSubmissionAdminVO> listCampaignSubmissions(Long campaignId);
}
