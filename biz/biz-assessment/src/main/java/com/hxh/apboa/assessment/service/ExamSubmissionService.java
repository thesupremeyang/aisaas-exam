package com.hxh.apboa.assessment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hxh.apboa.common.entity.ExamSubmission;

import java.time.LocalDateTime;

public interface ExamSubmissionService extends IService<ExamSubmission> {
    ExamSubmission getOwned(Long submissionId, Long accountId);
    boolean saveDraft(Long submissionId, ExamSubmission draft, Long accountId);
    ExamSubmission submit(Long submissionId, Long accountId);
    ExamSubmission retryAssessment(Long submissionId, Long accountId);
    ExamSubmission forceSubmitExpired(Long submissionId, LocalDateTime now);
}
