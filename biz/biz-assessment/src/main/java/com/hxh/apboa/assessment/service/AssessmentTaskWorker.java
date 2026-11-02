package com.hxh.apboa.assessment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hxh.apboa.assessment.mapper.AssessmentReportMapper;
import com.hxh.apboa.assessment.mapper.AssessmentTaskMapper;
import com.hxh.apboa.assessment.mapper.ExamCampaignMapper;
import com.hxh.apboa.assessment.mapper.ExamEnrollmentMapper;
import com.hxh.apboa.assessment.mapper.ExamSubmissionMapper;
import com.hxh.apboa.common.entity.AssessmentReport;
import com.hxh.apboa.common.entity.AssessmentTask;
import com.hxh.apboa.common.entity.ExamCampaign;
import com.hxh.apboa.common.entity.ExamSubmission;
import com.hxh.apboa.common.util.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Map;

/** 轮询初评队列，生成结构化初评报告；后续可通过 InitialAssessmentClient 替换为模型调用实现。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AssessmentTaskWorker {
    private final AssessmentTaskMapper taskMapper;
    private final AssessmentReportMapper reportMapper;
    private final ExamSubmissionMapper submissionMapper;
    private final ExamEnrollmentMapper enrollmentMapper;
    private final ExamCampaignMapper campaignMapper;
    private final InitialAssessmentClient assessmentClient;
    private final ObjectMapper objectMapper;
    private final RuntimeAssessmentProperties runtimeProperties;

    /** AI 智评屏蔽开关：false（默认）时不再消费初评队列，交卷后的初评任务不再执行。 */
    @Value("${assessment.initial-assessment.enabled:false}")
    private boolean initialAssessmentEnabled;

    @Scheduled(fixedDelayString = "${assessment.worker.fixed-delay-ms:3000}")
    public void poll() {
        if (!initialAssessmentEnabled) {
            // AI 智评已屏蔽：保持队列原样，历史 QUEUED 任务也不再执行。
            return;
        }
        // 后台定时线程无请求级租户上下文，需显式忽略租户过滤以跨租户扫描待处理任务
        TenantUtils.setIgnoreTenant();
        try {
            var task = taskMapper.selectOne(new LambdaQueryWrapper<AssessmentTask>()
                    .eq(AssessmentTask::getStatus, "QUEUED")
                    .orderByAsc(AssessmentTask::getCreatedAt)
                    .last("limit 1"));
            if (task != null) {
                process(task);
            }
        } finally {
            TenantUtils.clearIgnoreTenant();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void process(AssessmentTask task) {
        // 以任务自身租户建立上下文，保证后续 submission/report 读写归属正确
        TenantUtils.setCurrentTenant(task.getTenantId(), null);
        // poll() 为跨租户取队列任务设置了忽略标记；真正处理时必须恢复租户过滤，避免跨租户读写。
        TenantUtils.clearIgnoreTenant();
        try {
            AssessmentTaskStateMachine.requireTransition(task.getStatus(), "RUNNING");
            task.setStatus("RUNNING");
            task.setProgress(35);
            task.setStartedAt(LocalDateTime.now());
            taskMapper.updateById(task);

        try {
            var submission = submissionMapper.selectById(task.getSubmissionId());
            if (submission == null) {
                throw new IllegalArgumentException("初评任务关联的作品不存在");
            }
            var campaign = findCampaign(submission);
            var rubric = AssessmentRubric.fromCampaignConfig(campaign == null ? null : campaign.getConfigJson());
            var result = assessmentClient.assess(submission.getFormJson(), rubric);
            var report = new AssessmentReport();
            report.setSubmissionId(submission.getId());
            report.setTaskId(task.getId());
            report.setRubricVersion(campaign == null || campaign.getRubricVersion() == null
                    ? "v1.0" : campaign.getRubricVersion());
            report.setPromptVersion(runtimeProperties.promptVersion());
            report.setModelName(assessmentClient.getClass().getSimpleName());
            report.setTotalScore(result.totalScore());
            report.setConfidence(result.confidence());
            report.setReportJson(toJson(result));
            reportMapper.insert(report);

            AssessmentTaskStateMachine.requireTransition("RUNNING", "SUCCEEDED");
            task.setStatus("SUCCEEDED");
            task.setProgress(100);
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            // 超时交卷需要保留来源状态，便于门户和评审端区分系统自动交卷。
            submission.setStatus("TIMEOUT_SUBMITTED".equals(submission.getStatus())
                    ? "TIMEOUT_SUBMITTED" : "REVIEW_PENDING");
            submissionMapper.updateById(submission);
        } catch (RuntimeException ex) {
            fail(task, ex);
        }
        } finally {
            TenantUtils.clear();
        }
    }

    private ExamCampaign findCampaign(ExamSubmission submission) {
        if (submission.getEnrollmentId() == null) {
            return null;
        }
        var enrollment = enrollmentMapper.selectById(submission.getEnrollmentId());
        if (enrollment == null || enrollment.getCampaignId() == null) {
            return null;
        }
        return campaignMapper.selectById(enrollment.getCampaignId());
    }

    private String toJson(InitialAssessmentClient.InitialAssessmentResult result) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "dimensionScores", result.dimensionScores(), "totalScore", result.totalScore(),
                    "confidence", result.confidence(), "conclusion", result.conclusion(), "evidence", result.evidence(),
                    "risks", result.risks(), "suggestions", result.suggestions()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("初评报告序列化失败", e);
        }
    }

    private void fail(AssessmentTask task, RuntimeException ex) {
        task.setStatus("FAILED");
        task.setErrorCode(errorCode(ex));
        task.setErrorMessage(ex.getMessage() == null || ex.getMessage().isBlank()
                ? "AI 智评失败，请稍后重新提交" : ex.getMessage());
        task.setFinishedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        log.warn("AI 智评任务失败 taskId={}, submissionId={}, tenantId={}, errorCode={}, message={}",
                task.getId(), task.getSubmissionId(), task.getTenantId(), task.getErrorCode(), task.getErrorMessage(), ex);
    }

    private String errorCode(RuntimeException ex) {
        if (ex instanceof RuntimeAssessmentException runtimeException) {
            return switch (runtimeException.kind()) {
                case TIMEOUT -> "INITIAL_ASSESSMENT_TIMEOUT";
                case CONNECTION -> "INITIAL_ASSESSMENT_CONNECTION";
                case HTTP_STATUS -> "INITIAL_ASSESSMENT_HTTP_STATUS";
                case INVALID_RESPONSE -> "INITIAL_ASSESSMENT_INVALID_RESPONSE";
            };
        }
        return "INITIAL_ASSESSMENT_FAILED";
    }
}
