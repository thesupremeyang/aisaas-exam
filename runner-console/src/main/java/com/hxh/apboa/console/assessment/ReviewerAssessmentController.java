package com.hxh.apboa.console.assessment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hxh.apboa.assessment.service.AssessmentReviewService;
import com.hxh.apboa.common.entity.AssessmentReview;
import com.hxh.apboa.common.exception.NotAuthException;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.common.util.UserUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 旧版单题人工复核接口。
 *
 * <p>前端已无入口（页面为死路由），但接口仍对外可达，此前无任何角色校验。
 * 新流程统一走 {@code /exam/reviewer/scores}，这里的 publish 与新版保存路径状态语义冲突
 * （旧版要求 DRAFT、新版写入即为 PUBLISHED），因此仅保留读取能力并统一加平台管理员校验。
 */
@RestController
@RequestMapping("/exam/reviewer/submissions")
@RequiredArgsConstructor
public class ReviewerAssessmentController {
    private final AssessmentReviewService reviewService;

    @GetMapping("/{submissionId}/review")
    public R<AssessmentReview> detail(@PathVariable Long submissionId) {
        requirePlatformAdmin();
        return R.data(reviewService.getOne(new LambdaQueryWrapper<AssessmentReview>()
                .eq(AssessmentReview::getSubmissionId, submissionId)));
    }

    @PutMapping("/{submissionId}/review")
    public R<Boolean> save(@PathVariable Long submissionId, @RequestBody AssessmentReview review) {
        requirePlatformAdmin();
        review.setSubmissionId(submissionId);
        return R.data(reviewService.saveDraft(review, UserUtils.getId()) != null);
    }

    /**
     * 旧版发布：写入 assessment_result。
     * 与新流程状态语义冲突（新流程 saveFinalScore 已写 PUBLISHED，此处要求 DRAFT），
     * 正常路径下不可达；保留但加鉴权，避免被直接调用覆盖成绩。
     */
    @PostMapping("/{submissionId}/review/publish")
    public R<AssessmentReview> publish(@PathVariable Long submissionId) {
        requirePlatformAdmin();
        return R.data(reviewService.publish(submissionId, UserUtils.getId()));
    }

    private void requirePlatformAdmin() {
        if (!UserUtils.isPlatformAdmin()) {
            throw new NotAuthException("仅平台 admin 可复核考试成绩");
        }
    }
}
