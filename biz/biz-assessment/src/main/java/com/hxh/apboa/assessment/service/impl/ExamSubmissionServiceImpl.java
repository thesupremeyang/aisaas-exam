package com.hxh.apboa.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hxh.apboa.agent.mapper.AgentDefinitionMapper;
import com.hxh.apboa.assessment.mapper.AssessmentReportMapper;
import com.hxh.apboa.assessment.mapper.ExamCampaignMapper;
import com.hxh.apboa.assessment.mapper.AssessmentTaskMapper;
import com.hxh.apboa.assessment.mapper.ExamEnrollmentMapper;
import com.hxh.apboa.assessment.mapper.ExamSubmissionMapper;
import com.hxh.apboa.assessment.service.ExamCampaignTiming;
import com.hxh.apboa.assessment.service.ExamSubmissionService;
import com.hxh.apboa.assessment.service.AssessmentTaskStateMachine;
import com.hxh.apboa.common.entity.AssessmentReport;
import com.hxh.apboa.common.entity.AssessmentTask;
import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.entity.ExamCampaign;
import com.hxh.apboa.common.entity.ExamSubmission;
import com.hxh.apboa.common.exception.BusinessException;
import com.hxh.apboa.common.util.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ExamSubmissionServiceImpl extends ServiceImpl<ExamSubmissionMapper, ExamSubmission> implements ExamSubmissionService {
    private final ExamEnrollmentMapper enrollmentMapper;
    private final AssessmentTaskMapper taskMapper;
    private final AssessmentReportMapper reportMapper;
    private final AgentDefinitionMapper agentDefinitionMapper;
    private final ExamCampaignMapper campaignMapper;

    /** AI 智评屏蔽开关：false（默认）时交卷不再排队初评任务，考生端展示固定完成提示。 */
    @Value("${assessment.initial-assessment.enabled:false}")
    private boolean initialAssessmentEnabled;

    @Override
    public ExamSubmission getOwned(Long submissionId, Long accountId) {
        var submission = getById(submissionId);
        if (submission == null) {
            throw new IllegalArgumentException("作品提交不存在");
        }
        var enrollment = enrollmentMapper.selectById(submission.getEnrollmentId());
        if (enrollment == null || !accountId.equals(enrollment.getAccountId())) {
            throw new IllegalStateException("无权访问该作品提交");
        }
        fillAgentName(submission);
        fillExamTiming(submission, findCampaign(enrollment.getCampaignId()), LocalDateTime.now());
        return submission;
    }

    @Override
    public boolean saveDraft(Long submissionId, ExamSubmission draft, Long accountId) {
        // 上传附件后会紧接着触发草稿保存，自动保存与其他请求可能短暂竞争同一版本。
        // 重新读取最新版本并重试，避免瞬时竞争直接暴露为保存失败。
        for (int attempt = 0; attempt < 3; attempt++) {
            var submission = getOwned(submissionId, accountId);
            if (!"DRAFT".equals(submission.getStatus())) {
                throw new IllegalStateException("作品已提交，不能再编辑");
            }
            ensureSubmissionOpen(findCampaignForSubmission(submission), LocalDateTime.now());

            int version = submission.getVersion() == null ? 0 : submission.getVersion();
            var update = new LambdaUpdateWrapper<ExamSubmission>()
                    .set(ExamSubmission::getFormJson, draft.getFormJson())
                    .set(ExamSubmission::getCurrentStep, draft.getCurrentStep())
                    .set(ExamSubmission::getVersion, version + 1)
                    .eq(ExamSubmission::getId, submissionId)
                    .eq(ExamSubmission::getStatus, "DRAFT")
                    .eq(ExamSubmission::getVersion, version);
            // 2026-09-05 事故复盘：agentId 原先无条件 set，前端某次自动保存（页面重载、
            // 多标签页、退回上一步）带上空 agentId 时，会把已入库的 agentId 覆盖成 null，
            // 考生随后点交卷被「请选择平台内智能体后再提交」硬拒（当天 267 次）。
            // 与下方 workType 保持一致：null 表示“本次未修改”，不参与覆盖。
            if (draft.getAgentId() != null) {
                update.set(ExamSubmission::getAgentId, draft.getAgentId());
            }
            if (draft.getWorkType() != null) {
                update.set(ExamSubmission::getWorkType, draft.getWorkType());
            }
            int changed = baseMapper.update(null, update);
            if (changed == 1) {
                return true;
            }
        }
        throw new IllegalStateException("作品已被其他页面提交或修改，请刷新后重试");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamSubmission submit(Long submissionId, Long accountId) {
        var submission = getOwned(submissionId, accountId);
        if (!"DRAFT".equals(submission.getStatus())) {
            return submission;
        }
        var campaign = findCampaignForSubmission(submission);
        var now = LocalDateTime.now();
        var state = ExamCampaignTiming.state(campaign, now);
        if (state == ExamCampaignTiming.State.WAITING) {
            throw new BusinessException("考试尚未开始，暂不能提交");
        }
        if (state == ExamCampaignTiming.State.ENDED) {
            return forceSubmitExpired(submissionId, now);
        }
        if (submission.getAgentId() == null) {
            throw new IllegalStateException("请选择平台内智能体后再提交");
        }
        long duplicate = baseMapper.selectCount(new LambdaQueryWrapper<ExamSubmission>()
                .eq(ExamSubmission::getEnrollmentId, submission.getEnrollmentId())
                .eq(ExamSubmission::getAgentId, submission.getAgentId())
                .ne(ExamSubmission::getId, submissionId)
                .apply("del_flag = 0"));
        if (duplicate > 0) {
            throw new IllegalStateException("该智能体已在本批次提交过，请勿重复提交");
        }
        var agent = agentDefinitionMapper.selectOne(new LambdaQueryWrapper<AgentDefinition>()
                .eq(AgentDefinition::getId, submission.getAgentId())
                .eq(AgentDefinition::getTenantId, TenantUtils.getCurrentTenantId())
                .eq(AgentDefinition::getEnabled, true));
        if (agent == null) {
            throw new IllegalStateException("所选智能体不存在或不属于当前账号");
        }

        return transitionToSubmitted(submission, "AI_QUEUED", now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamSubmission retryAssessment(Long submissionId, Long accountId) {
        if (!initialAssessmentEnabled) {
            throw new BusinessException("AI 智评暂未开启，无需重新提交");
        }
        var submission = getOwned(submissionId, accountId);
        if (!"AI_QUEUED".equals(submission.getStatus())
                && !"REVIEW_PENDING".equals(submission.getStatus())
                && !"TIMEOUT_SUBMITTED".equals(submission.getStatus())) {
            throw new IllegalStateException("当前作品状态不支持重新提交 AI 智评");
        }

        var campaign = findCampaignForSubmission(submission);
        if (campaign == null) {
            throw new IllegalStateException("考试批次不存在，无法重新提交 AI 智评");
        }
        var now = LocalDateTime.now();
        if (campaign.getReviewEnd() != null && !now.isBefore(campaign.getReviewEnd())) {
            throw new BusinessException("评审时间已结束，无法重新提交 AI 智评");
        }

        var task = latestAssessmentTask(submissionId);
        if (task == null) {
            throw new IllegalStateException("未找到 AI 智评任务，请联系考评管理员");
        }
        if ("QUEUED".equals(task.getStatus()) || "RUNNING".equals(task.getStatus())) {
            return submission;
        }
        if ("SUCCEEDED".equals(task.getStatus())) {
            throw new IllegalStateException("AI 智评已经完成，无需重复提交");
        }
        AssessmentTaskStateMachine.requireTransition(task.getStatus(), "QUEUED");

        int nextAttempt = (task.getAttempt() == null ? 0 : task.getAttempt()) + 1;
        int changed = taskMapper.update(null, new LambdaUpdateWrapper<AssessmentTask>()
                .set(AssessmentTask::getStatus, "QUEUED")
                .set(AssessmentTask::getProgress, 10)
                .set(AssessmentTask::getAttempt, nextAttempt)
                .set(AssessmentTask::getErrorCode, null)
                .set(AssessmentTask::getErrorMessage, null)
                .set(AssessmentTask::getStartedAt, null)
                .set(AssessmentTask::getFinishedAt, null)
                .eq(AssessmentTask::getId, task.getId())
                .eq(AssessmentTask::getStatus, "FAILED"));
        if (changed != 1) {
            var latest = latestAssessmentTask(submissionId);
            if (latest != null && ("QUEUED".equals(latest.getStatus()) || "RUNNING".equals(latest.getStatus()))) {
                return submission;
            }
            throw new IllegalStateException("AI 智评任务状态已变化，请刷新后重试");
        }

        // 失败任务理论上不会有报告；若报告已在任务后半段写入，则先软删除，避免新报告触发唯一键冲突。
        reportMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<AssessmentReport>()
                .eq("submission_id", submissionId)
                .eq("del_flag", 0)
                .set("del_flag", 1));
        return getOwned(submissionId, accountId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamSubmission forceSubmitExpired(Long submissionId, LocalDateTime now) {
        var submission = getById(submissionId);
        if (submission == null) {
            throw new IllegalArgumentException("作品提交不存在");
        }
        var enrollment = enrollmentMapper.selectById(submission.getEnrollmentId());
        var campaign = enrollment == null ? null : findCampaign(enrollment.getCampaignId());
        if (ExamCampaignTiming.state(campaign, now) != ExamCampaignTiming.State.ENDED) {
            throw new BusinessException("考试尚未到截止时间，不能执行超时交卷");
        }
        if (!"DRAFT".equals(submission.getStatus())) {
            fillExamTiming(submission, campaign, now);
            return submission;
        }
        // 2026-09-05 事故复盘：原先一律记为 TIMEOUT_SUBMITTED，导致 80 份空卷与真实作品
        // 混在一起、还照样排队 AI 智评，既烧算力又污染成绩复核列表（后来不得不在
        // ExamReviewerScoreServiceImpl 里额外过滤）。这里按内容分级落状态。
        return transitionToSubmitted(submission, resolveTimeoutStatus(submission), now);
    }

    /**
     * 超时收卷状态分级：
     * - NO_ANSWER：既没绑定智能体也没填任何内容 → 未作答
     * - PARTIAL_TIMEOUT_SUBMITTED：只完成了一部分 → 不完整
     * - TIMEOUT_SUBMITTED：内容完整，只是没来得及点交卷 → 有效作品
     */
    private String resolveTimeoutStatus(ExamSubmission submission) {
        boolean hasAgent = submission.getAgentId() != null;
        boolean hasAnswer = hasAnswer(submission);
        if (!hasAgent && !hasAnswer) {
            return "NO_ANSWER";
        }
        if (!hasAgent || !hasAnswer) {
            return "PARTIAL_TIMEOUT_SUBMITTED";
        }
        return "TIMEOUT_SUBMITTED";
    }

    /** form_json 是否含有实质内容（排除 null / 空串 / {} / "null" / "\"\""） */
    private boolean hasAnswer(ExamSubmission submission) {
        String formJson = submission.getFormJson();
        if (formJson == null) {
            return false;
        }
        String trimmed = formJson.trim();
        return !trimmed.isEmpty()
                && !"{}".equals(trimmed)
                && !"\"\"".equals(trimmed)
                && !"null".equalsIgnoreCase(trimmed);
    }

    private ExamSubmission transitionToSubmitted(ExamSubmission submission, String status, LocalDateTime now) {
        int version = submission.getVersion() == null ? 0 : submission.getVersion();
        int changed = baseMapper.update(null, new LambdaUpdateWrapper<ExamSubmission>()
                .set(ExamSubmission::getStatus, status)
                .set(ExamSubmission::getSubmittedAt, now)
                .set(ExamSubmission::getVersion, version + 1)
                .eq(ExamSubmission::getId, submission.getId())
                .eq(ExamSubmission::getStatus, "DRAFT")
                .eq(ExamSubmission::getVersion, version));
        if (changed == 1) {
            submission.setStatus(status);
            submission.setSubmittedAt(now);
            submission.setVersion(version + 1);
            // NO_ANSWER / PARTIAL 不是有效作品：没有绑定智能体时 AI 智评也无法定位作品，
            // 让它们排队只会白烧算力并污染复核列表。
            if (shouldQueueAssessment(status)) {
                createAssessmentTaskIfAbsent(submission);
            }
        } else {
            var latest = getById(submission.getId());
            if (latest != null && "DRAFT".equals(latest.getStatus())) {
                throw new BusinessException("作品正在保存，请稍后重试提交");
            }
            if (latest != null) {
                submission = latest;
            }
        }
        var enrollment = enrollmentMapper.selectById(submission.getEnrollmentId());
        fillExamTiming(submission, enrollment == null ? null : findCampaign(enrollment.getCampaignId()), now);
        return submission;
    }

    /** 只有完整/主动交卷的作品才进入 AI 智评队列 */
    private boolean shouldQueueAssessment(String status) {
        return !"NO_ANSWER".equals(status) && !"PARTIAL_TIMEOUT_SUBMITTED".equals(status);
    }

    private void createAssessmentTaskIfAbsent(ExamSubmission submission) {
        if (!initialAssessmentEnabled) {
            // AI 智评已屏蔽：交卷后不再排队初评任务，考生端直接展示完成提示。
            return;
        }
        var existing = taskMapper.selectOne(new LambdaQueryWrapper<AssessmentTask>()
                .eq(AssessmentTask::getSubmissionId, submission.getId())
                .eq(AssessmentTask::getTaskType, "INITIAL_ASSESSMENT")
                .in(AssessmentTask::getStatus, "QUEUED", "RUNNING")
                .orderByDesc(AssessmentTask::getCreatedAt)
                .last("limit 1"));
        if (existing != null) {
            return;
        }
        var task = new AssessmentTask();
        task.setSubmissionId(submission.getId());
        task.setTenantId(submission.getTenantId());
        task.setTaskType("INITIAL_ASSESSMENT");
        task.setStatus("QUEUED");
        task.setProgress(10);
        task.setAttempt(0);
        taskMapper.insert(task);
    }

    private AssessmentTask latestAssessmentTask(Long submissionId) {
        return taskMapper.selectOne(new LambdaQueryWrapper<AssessmentTask>()
                .eq(AssessmentTask::getSubmissionId, submissionId)
                .eq(AssessmentTask::getTaskType, "INITIAL_ASSESSMENT")
                .orderByDesc(AssessmentTask::getCreatedAt)
                .last("limit 1"));
    }

    private ExamCampaign findCampaignForSubmission(ExamSubmission submission) {
        var enrollment = enrollmentMapper.selectById(submission.getEnrollmentId());
        return enrollment == null ? null : findCampaign(enrollment.getCampaignId());
    }

    private ExamCampaign findCampaign(Long campaignId) {
        return campaignId == null ? null : campaignMapper.selectById(campaignId);
    }

    private void fillAgentName(ExamSubmission submission) {
        if (submission.getAgentId() == null || submission.getTenantId() == null) {
            return;
        }
        var agent = agentDefinitionMapper.selectOne(new LambdaQueryWrapper<AgentDefinition>()
                .select(AgentDefinition::getName)
                .eq(AgentDefinition::getId, submission.getAgentId())
                .eq(AgentDefinition::getTenantId, submission.getTenantId()));
        if (agent != null) {
            submission.setAgentName(agent.getName());
        }
    }

    private void ensureSubmissionOpen(ExamCampaign campaign, LocalDateTime now) {
        var state = ExamCampaignTiming.state(campaign, now);
        if (state == ExamCampaignTiming.State.WAITING) {
            throw new BusinessException("考试尚未开始，暂不能保存答题内容");
        }
        if (state == ExamCampaignTiming.State.ENDED) {
            throw new BusinessException("考试时间已结束，系统正在自动交卷");
        }
    }

    private void fillExamTiming(ExamSubmission submission, ExamCampaign campaign, LocalDateTime now) {
        submission.setExamState(ExamCampaignTiming.state(campaign, now).name());
        submission.setExamStartAt(campaign == null ? null : campaign.getSubmissionStart());
        submission.setExamEndAt(campaign == null ? null : campaign.getSubmissionEnd());
        submission.setServerNow(now);
    }
}
