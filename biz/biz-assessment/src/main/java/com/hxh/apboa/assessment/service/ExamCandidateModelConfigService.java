package com.hxh.apboa.assessment.service;

import com.hxh.apboa.common.dto.ExamCandidateModelConfigRequest;
import com.hxh.apboa.common.vo.ExamCandidateModelConfigVO;

import java.util.List;

public interface ExamCandidateModelConfigService {
    ExamCandidateModelConfigVO get(Long campaignId, Long accountId);

    ExamCandidateModelConfigVO save(Long campaignId, Long accountId, ExamCandidateModelConfigRequest request);

    List<String> listAvailableModels(Long campaignId, Long accountId, ExamCandidateModelConfigRequest request);
}
