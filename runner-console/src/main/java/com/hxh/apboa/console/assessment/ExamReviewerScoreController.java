package com.hxh.apboa.console.assessment;

import com.hxh.apboa.assessment.dto.ExamScorePublishVO;
import com.hxh.apboa.assessment.dto.ExamScoreRowVO;
import com.hxh.apboa.assessment.dto.ExamSubmissionDetailVO;
import com.hxh.apboa.assessment.service.ExamReviewerScoreService;
import com.hxh.apboa.common.exception.NotAuthException;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.common.util.UserUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/exam/reviewer/scores")
@RequiredArgsConstructor
public class ExamReviewerScoreController {

    private final ExamReviewerScoreService scoreService;

    @GetMapping
    public R<List<ExamScoreRowVO>> list(@RequestParam(required = false) Long campaignId,
                                        @RequestParam(required = false) String name) {
        requirePlatformAdmin();
        return R.data(scoreService.listScores(campaignId, name));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam("campaignId") Long campaignId) {
        requirePlatformAdmin();
        byte[] content = scoreService.exportScores(campaignId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("考试成绩汇总.xlsx", StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(content.length);
        return new ResponseEntity<>(content, headers, HttpStatus.OK);
    }

    @GetMapping("/{submissionId}/detail")
    public R<ExamSubmissionDetailVO> detail(@PathVariable Long submissionId) {
        requirePlatformAdmin();
        return R.data(scoreService.getSubmissionDetail(submissionId));
    }

    @GetMapping("/enrollments/{enrollmentId}/detail")
    public R<ExamSubmissionDetailVO> enrollmentDetail(@PathVariable Long enrollmentId) {
        requirePlatformAdmin();
        return R.data(scoreService.getEnrollmentDetail(enrollmentId));
    }

    @PutMapping("/{submissionId}")
    public R<Boolean> saveFinalScore(@PathVariable Long submissionId,
                                     @RequestParam Integer finalScore,
                                     @RequestParam(required = false, defaultValue = "") String comment) {
        requirePlatformAdmin();
        scoreService.saveFinalScore(submissionId, finalScore, comment, UserUtils.getId());
        return R.data(true);
    }

    /** 录入（或清空）单个考生的理论分，满分 100。 */
    @PutMapping("/enrollments/{enrollmentId}/theory-score")
    public R<Boolean> saveTheoryScore(@PathVariable Long enrollmentId,
                                      @RequestParam(required = false) Integer score) {
        requirePlatformAdmin();
        scoreService.saveTheoryScore(enrollmentId, score);
        return R.data(true);
    }

    /** 批量录入理论分，请求体为 {报名记录ID: 理论分}。 */
    @PostMapping("/campaigns/{campaignId}/theory-scores")
    public R<Integer> batchSaveTheoryScore(@PathVariable Long campaignId,
                                           @RequestBody Map<Long, Integer> scores) {
        requirePlatformAdmin();
        return R.data(scoreService.batchSaveTheoryScore(campaignId, scores));
    }

    /**
     * 发布本场成绩：把已复核的本题分汇总固化为报名记录的实操分，并开启查分。
     * force 为 false 且仍有作品未复核时拒绝发布。
     */
    @PostMapping("/campaigns/{campaignId}/publish")
    public R<ExamScorePublishVO> publish(@PathVariable Long campaignId,
                                         @RequestParam(required = false, defaultValue = "false") boolean force) {
        requirePlatformAdmin();
        return R.data(scoreService.publishCampaignScores(campaignId, UserUtils.getId(), force));
    }

    private void requirePlatformAdmin() {
        if (!UserUtils.isPlatformAdmin()) {
            throw new NotAuthException("仅平台 admin 可管理考试成绩");
        }
    }
}
