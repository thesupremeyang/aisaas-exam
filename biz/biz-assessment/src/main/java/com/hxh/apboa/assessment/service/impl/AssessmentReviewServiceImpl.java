package com.hxh.apboa.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hxh.apboa.assessment.mapper.AssessmentResultMapper;
import com.hxh.apboa.assessment.mapper.AssessmentReviewMapper;
import com.hxh.apboa.assessment.mapper.ExamCampaignMapper;
import com.hxh.apboa.assessment.mapper.ExamEnrollmentMapper;
import com.hxh.apboa.assessment.mapper.ExamSubmissionMapper;
import com.hxh.apboa.assessment.service.AssessmentReviewService;
import com.hxh.apboa.assessment.service.AssessmentRubric;
import com.hxh.apboa.common.entity.AssessmentResult;
import com.hxh.apboa.common.entity.AssessmentReview;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AssessmentReviewServiceImpl extends ServiceImpl<AssessmentReviewMapper, AssessmentReview> implements AssessmentReviewService {
    private final AssessmentResultMapper resultMapper;
    private final ExamSubmissionMapper submissionMapper;
    private final ExamEnrollmentMapper enrollmentMapper;
    private final ExamCampaignMapper campaignMapper;

    @Override
    public AssessmentReview saveDraft(AssessmentReview review, Long reviewerId) {
        var existing = review.getId() == null ? null : getById(review.getId());
        if (existing != null && !reviewerId.equals(existing.getReviewerAccountId())) {
            throw new IllegalArgumentException("无权修改其他评委的复核意见");
        }
        if (existing != null && "PUBLISHED".equals(existing.getStatus())) {
            throw new IllegalArgumentException("已发布的复核结论不可修改");
        }
        var submission = review.getSubmissionId() == null ? null : submissionMapper.selectById(review.getSubmissionId());
        var enrollment = submission == null ? null : enrollmentMapper.selectById(submission.getEnrollmentId());
        var campaign = enrollment == null ? null : campaignMapper.selectById(enrollment.getCampaignId());
        var rubric = AssessmentRubric.fromCampaignConfig(campaign == null ? null : campaign.getConfigJson());
        if (review.getTotalScore() == null || review.getTotalScore() < 0 || review.getTotalScore() > rubric.totalScore()) {
            throw new IllegalArgumentException("复核总分必须在 0 到 " + rubric.totalScore() + " 之间");
        }
        if (review.getComment() == null || review.getComment().trim().length() < 10) {
            throw new IllegalArgumentException("复核意见至少 10 个字符");
        }
        review.setId(existing == null ? null : existing.getId());
        review.setReviewerAccountId(reviewerId);
        review.setStatus("DRAFT");
        return saveOrUpdate(review) ? review : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssessmentReview publish(Long submissionId, Long reviewerId) {
        var review = getOne(new LambdaQueryWrapper<AssessmentReview>()
                .eq(AssessmentReview::getSubmissionId, submissionId)
                .eq(AssessmentReview::getReviewerAccountId, reviewerId));
        if (review == null) throw new IllegalArgumentException("请先保存复核意见");
        if (!"DRAFT".equals(review.getStatus())) throw new IllegalArgumentException("当前复核意见不可发布");

        review.setStatus("PUBLISHED");
        updateById(review);
        var result = resultMapper.selectOne(new LambdaQueryWrapper<AssessmentResult>()
                .eq(AssessmentResult::getSubmissionId, submissionId));
        if (result == null) {
            result = new AssessmentResult();
            result.setSubmissionId(submissionId);
            result.setVersion(1);
        } else {
            result.setVersion((result.getVersion() == null ? 0 : result.getVersion()) + 1);
        }
        var submission = submissionMapper.selectById(submissionId);
        if (submission == null) throw new IllegalArgumentException("作品提交不存在");
        var enrollment = enrollmentMapper.selectById(submission.getEnrollmentId());
        if (enrollment == null) throw new IllegalArgumentException("作品报名记录不存在");
        var campaign = campaignMapper.selectById(enrollment.getCampaignId());
        if (campaign == null) throw new IllegalArgumentException("考试批次不存在");
        var rubric = AssessmentRubric.fromCampaignConfig(campaign.getConfigJson());
        if (review.getTotalScore() > rubric.totalScore()) {
            throw new IllegalArgumentException("复核总分超过当前考试批次量表满分");
        }
        result.setFinalScore(review.getTotalScore());
        result.setLevel(rubric.levelOf(review.getTotalScore()));
        result.setPublishedBy(reviewerId);
        result.setPublishedAt(LocalDateTime.now());
        if (result.getId() == null) resultMapper.insert(result); else resultMapper.updateById(result);

        submission.setStatus("PUBLISHED");
        submissionMapper.updateById(submission);
        return review;
    }
}
