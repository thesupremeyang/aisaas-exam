package com.hxh.apboa.assessment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hxh.apboa.assessment.mapper.ExamCampaignMapper;
import com.hxh.apboa.assessment.mapper.ExamEnrollmentMapper;
import com.hxh.apboa.assessment.mapper.ExamSubmissionMapper;
import com.hxh.apboa.common.entity.ExamCampaign;
import com.hxh.apboa.common.entity.ExamEnrollment;
import com.hxh.apboa.common.entity.ExamSubmission;
import com.hxh.apboa.common.util.TenantUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 处理已经超过考试截止时间但仍处于草稿状态的作品。
 *
 * <p>浏览器倒计时只负责提示，真正的超时交卷由服务端定时任务完成，
 * 因此考生断网或关闭页面也不会绕过截止时间。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExamDeadlineWorker {
    private final ExamSubmissionMapper submissionMapper;
    private final ExamEnrollmentMapper enrollmentMapper;
    private final ExamCampaignMapper campaignMapper;
    private final ExamSubmissionService submissionService;

    @Scheduled(fixedDelayString = "${exam.deadline-worker.fixed-delay-ms:1000}")
    public void poll() {
        // 定时线程没有请求级租户上下文，需要跨租户扫描考试草稿。
        TenantUtils.setIgnoreTenant();
        try {
            var now = LocalDateTime.now();
            var drafts = submissionMapper.selectList(new LambdaQueryWrapper<ExamSubmission>()
                    .eq(ExamSubmission::getStatus, "DRAFT")
                    .orderByAsc(ExamSubmission::getUpdatedAt));
            for (var submission : drafts) {
                if (isExpired(submission, now)) {
                    try {
                        submissionService.forceSubmitExpired(submission.getId(), now);
                    } catch (RuntimeException exception) {
                        // 单份作品失败不能阻塞其他考生；下一轮继续重试。
                        log.warn("考试超时交卷失败，submissionId={}", submission.getId(), exception);
                    }
                }
            }
        } finally {
            TenantUtils.clearIgnoreTenant();
        }
    }

    private boolean isExpired(ExamSubmission submission, LocalDateTime now) {
        ExamEnrollment enrollment = enrollmentMapper.selectById(submission.getEnrollmentId());
        if (enrollment == null) {
            return false;
        }
        ExamCampaign campaign = campaignMapper.selectById(enrollment.getCampaignId());
        return ExamCampaignTiming.state(campaign, now) == ExamCampaignTiming.State.ENDED;
    }
}
