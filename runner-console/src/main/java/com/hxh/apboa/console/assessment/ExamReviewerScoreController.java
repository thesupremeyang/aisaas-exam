package com.hxh.apboa.console.assessment;

import com.hxh.apboa.assessment.dto.ExamScoreRowVO;
import com.hxh.apboa.assessment.dto.ExamSubmissionDetailVO;
import com.hxh.apboa.assessment.service.ExamReviewerScoreService;
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

@RestController
@RequestMapping("/exam/reviewer/scores")
@RequiredArgsConstructor
public class ExamReviewerScoreController {

    private final ExamReviewerScoreService scoreService;

    @GetMapping
    public R<List<ExamScoreRowVO>> list(@RequestParam(required = false) Long campaignId,
                                        @RequestParam(required = false) String name) {
        return R.data(scoreService.listScores(campaignId, name));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam("campaignId") Long campaignId) {
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
        return R.data(scoreService.getSubmissionDetail(submissionId));
    }

    @GetMapping("/enrollments/{enrollmentId}/detail")
    public R<ExamSubmissionDetailVO> enrollmentDetail(@PathVariable Long enrollmentId) {
        return R.data(scoreService.getEnrollmentDetail(enrollmentId));
    }

    @PutMapping("/{submissionId}")
    public R<Boolean> saveFinalScore(@PathVariable Long submissionId,
                                     @RequestParam Integer finalScore,
                                     @RequestParam(required = false, defaultValue = "") String comment) {
        scoreService.saveFinalScore(submissionId, finalScore, comment, UserUtils.getId());
        return R.data(true);
    }
}
