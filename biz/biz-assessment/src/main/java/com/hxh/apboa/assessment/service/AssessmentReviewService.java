package com.hxh.apboa.assessment.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hxh.apboa.common.entity.AssessmentReview;
public interface AssessmentReviewService extends IService<AssessmentReview> {
    AssessmentReview saveDraft(AssessmentReview review, Long reviewerId);
    AssessmentReview publish(Long submissionId, Long reviewerId);
}
