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
import java.util.HashMap;
import java.util.Map;

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

    /** 单轮最多处理的草稿数，避免一次轮询把调度线程占满。 */
    private static final int BATCH_SIZE = 200;

    private final ExamSubmissionMapper submissionMapper;
    private final ExamEnrollmentMapper enrollmentMapper;
    private final ExamCampaignMapper campaignMapper;
    private final ExamSubmissionService submissionService;

    @Scheduled(fixedDelayString = "${exam.deadline-worker.fixed-delay-ms:5000}")
    public void poll() {
        // 定时线程没有请求级租户上下文，需要跨租户扫描考试草稿。
        TenantUtils.setIgnoreTenant();
        try {
            var now = LocalDateTime.now();
            // 只扫「场次已结束」的草稿：
            // 原实现每秒把全库 DRAFT 拉出来（无 limit、无场次过滤），
            // 每份草稿还要再查 2 次，考试期间 300 份草稿就是 ~900 SQL/s 的自压。
            var drafts = submissionMapper.selectList(new LambdaQueryWrapper<ExamSubmission>()
                    .eq(ExamSubmission::getStatus, "DRAFT")
                    .apply("enrollment_id IN (SELECT e.id FROM exam_enrollment e "
                                    + "JOIN exam_campaign c ON c.id = e.campaign_id "
                                    + "WHERE c.del_flag = 0 AND c.submission_end IS NOT NULL "
                                    + "AND c.submission_end <= {0})", now)
                    .orderByAsc(ExamSubmission::getUpdatedAt)
                    .last("LIMIT " + BATCH_SIZE));
            if (drafts.isEmpty()) {
                return;
            }
            if (drafts.size() >= BATCH_SIZE) {
                log.info("超时收卷本轮处理 {} 条（达到批量上限），仍有积压待下一轮", drafts.size());
            }

            // 同一轮内按场次/报名缓存，避免每份草稿重复查询。
            Map<Long, ExamEnrollment> enrollmentCache = new HashMap<>();
            Map<Long, ExamCampaign> campaignCache = new HashMap<>();

            for (var submission : drafts) {
                if (isExpired(submission, now, enrollmentCache, campaignCache)) {
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

    private boolean isExpired(ExamSubmission submission,
                              LocalDateTime now,
                              Map<Long, ExamEnrollment> enrollmentCache,
                              Map<Long, ExamCampaign> campaignCache) {
        ExamEnrollment enrollment = enrollmentCache.computeIfAbsent(
                submission.getEnrollmentId(), enrollmentMapper::selectById);
        if (enrollment == null) {
            return false;
        }
        ExamCampaign campaign = campaignCache.computeIfAbsent(
                enrollment.getCampaignId(), campaignMapper::selectById);
        return ExamCampaignTiming.state(campaign, now) == ExamCampaignTiming.State.ENDED;
    }
}
