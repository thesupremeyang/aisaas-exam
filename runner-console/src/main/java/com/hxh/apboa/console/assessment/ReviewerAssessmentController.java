package com.hxh.apboa.console.assessment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hxh.apboa.assessment.service.AssessmentReviewService;
import com.hxh.apboa.common.entity.AssessmentReview;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.common.util.UserUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/exam/reviewer/submissions")
@RequiredArgsConstructor
public class ReviewerAssessmentController {
    private final AssessmentReviewService reviewService;

    @GetMapping("/{submissionId}/review")
    public R<AssessmentReview> detail(@PathVariable Long submissionId) {
        return R.data(reviewService.getOne(new LambdaQueryWrapper<AssessmentReview>()
                .eq(AssessmentReview::getSubmissionId, submissionId)
                .eq(AssessmentReview::getReviewerAccountId, UserUtils.getId())));
    }

    @PutMapping("/{submissionId}/review")
    public R<Boolean> save(@PathVariable Long submissionId, @RequestBody AssessmentReview review) {
        review.setSubmissionId(submissionId);
        return R.data(reviewService.saveDraft(review, UserUtils.getId()) != null);
    }

    @PostMapping("/{submissionId}/review/publish")
    public R<AssessmentReview> publish(@PathVariable Long submissionId) {
        return R.data(reviewService.publish(submissionId, UserUtils.getId()));
    }
}
