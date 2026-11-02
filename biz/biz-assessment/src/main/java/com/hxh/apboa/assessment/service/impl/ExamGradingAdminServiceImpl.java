package com.hxh.apboa.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hxh.apboa.account.mapper.AccountMapper;
import com.hxh.apboa.account.mapper.TenantMapper;
import com.hxh.apboa.agent.mapper.AgentDefinitionMapper;
import com.hxh.apboa.assessment.dto.GradingCandidateRowVO;
import com.hxh.apboa.assessment.dto.GradingDetailVO;
import com.hxh.apboa.assessment.dto.GradingOverviewVO;
import com.hxh.apboa.assessment.dto.GradingQuestionCellVO;
import com.hxh.apboa.assessment.mapper.ExamGradingResultMapper;
import com.hxh.apboa.assessment.mapper.ExamGradingTaskMapper;
import com.hxh.apboa.assessment.mapper.ExamEnrollmentMapper;
import com.hxh.apboa.assessment.mapper.ExamQuestionMapper;
import com.hxh.apboa.assessment.mapper.ExamSubmissionMapper;
import com.hxh.apboa.assessment.mapper.ExamCampaignMapper;
import com.hxh.apboa.assessment.service.ExamGradingAdminService;
import com.hxh.apboa.assessment.service.ExamGradingLlmClient;
import com.hxh.apboa.assessment.service.ExamReviewerScoreService;
import com.hxh.apboa.assessment.service.ExamGradingRubricStore;
import com.hxh.apboa.common.entity.Account;
import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.entity.ExamCampaign;
import com.hxh.apboa.common.entity.ExamEnrollment;
import com.hxh.apboa.common.entity.ExamGradingResult;
import com.hxh.apboa.common.entity.ExamGradingTask;
import com.hxh.apboa.common.entity.ExamQuestion;
import com.hxh.apboa.common.entity.ExamSubmission;
import com.hxh.apboa.common.entity.Tenant;
import com.hxh.apboa.common.util.TenantUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamGradingAdminServiceImpl implements ExamGradingAdminService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ExamGradingTaskMapper taskMapper;
    private final ExamGradingResultMapper resultMapper;
    private final ExamSubmissionMapper submissionMapper;
    private final ExamEnrollmentMapper enrollmentMapper;
    private final ExamCampaignMapper campaignMapper;
    private final ExamQuestionMapper questionMapper;
    private final AccountMapper accountMapper;
    private final TenantMapper tenantMapper;
    private final AgentDefinitionMapper agentDefinitionMapper;
    private final ExamReviewerScoreService reviewerScoreService;
    private final ExamGradingRubricStore rubricStore;
    private final ExamGradingLlmClient llmClient;

    @Override
    public Map<String, Integer> startCampaignGrading(Long campaignId) {
        if (!llmClient.isConfigured()) {
            throw new IllegalArgumentException("AI 阅卷模型未配置：请先设置 ASSESSMENT_GRADING_API_KEY（endpoint/model 见 assessment.grading.*）");
        }
        ExamCampaign campaign = campaignMapper.selectById(campaignId);
        if (campaign == null) {
            throw new IllegalArgumentException("考试场次不存在");
        }
        TenantUtils.setIgnoreTenant();
        try {
            List<Long> enrollmentIds = enrollmentMapper.selectList(new LambdaQueryWrapper<ExamEnrollment>()
                            .eq(ExamEnrollment::getCampaignId, campaignId))
                    .stream().map(ExamEnrollment::getId).filter(Objects::nonNull).toList();
            if (enrollmentIds.isEmpty()) {
                throw new IllegalArgumentException("该场次暂无考生报名");
            }
            // 只评已提交且关联智能体的答卷；未作答/无智能体的记录没有可评配置
            List<ExamSubmission> submissions = submissionMapper.selectList(new LambdaQueryWrapper<ExamSubmission>()
                            .in(ExamSubmission::getEnrollmentId, enrollmentIds)
                            .ne(ExamSubmission::getStatus, "DRAFT")
                            .ne(ExamSubmission::getStatus, "NO_ANSWER")
                            .isNotNull(ExamSubmission::getAgentId))
                    .stream()
                    .filter(s -> s.getAgentId() != null)
                    .toList();
            if (submissions.isEmpty()) {
                throw new IllegalArgumentException("该场次暂无已提交且关联智能体的答卷");
            }
            Set<Long> activeSubmissionIds = taskMapper.selectList(new LambdaQueryWrapper<ExamGradingTask>()
                            .in(ExamGradingTask::getSubmissionId,
                                    submissions.stream().map(ExamSubmission::getId).toList())
                            .in(ExamGradingTask::getStatus, List.of("QUEUED", "RUNNING")))
                    .stream().map(ExamGradingTask::getSubmissionId).collect(Collectors.toSet());

            int enqueued = 0;
            int skippedRunning = 0;
            int withoutAgent = 0;
            for (ExamSubmission submission : submissions) {
                if (activeSubmissionIds.contains(submission.getId())) {
                    skippedRunning++;
                    continue;
                }
                enqueue(submission, campaignId);
                enqueued++;
            }
            Map<String, Integer> counts = new LinkedHashMap<>();
            counts.put("enqueued", enqueued);
            counts.put("skippedRunning", skippedRunning);
            counts.put("withoutAgent", withoutAgent);
            counts.put("total", submissions.size());
            return counts;
        } finally {
            TenantUtils.clearIgnoreTenant();
        }
    }

    @Override
    public void rerunSubmission(Long submissionId) {
        if (!llmClient.isConfigured()) {
            throw new IllegalArgumentException("AI 阅卷模型未配置：请先设置 ASSESSMENT_GRADING_API_KEY（endpoint/model 见 assessment.grading.*）");
        }
        TenantUtils.setIgnoreTenant();
        try {
            ExamSubmission submission = submissionMapper.selectById(submissionId);
            if (submission == null) {
                throw new IllegalArgumentException("答卷不存在");
            }
            if (submission.getAgentId() == null) {
                throw new IllegalArgumentException("该答卷未关联智能体，无法评分");
            }
            Long active = taskMapper.selectCount(new LambdaQueryWrapper<ExamGradingTask>()
                    .eq(ExamGradingTask::getSubmissionId, submissionId)
                    .in(ExamGradingTask::getStatus, List.of("QUEUED", "RUNNING")));
            if (active != null && active > 0) {
                throw new IllegalArgumentException("该答卷已有进行中的评分任务");
            }
            Long campaignId = resolveCampaignId(submission);
            enqueue(submission, campaignId);
        } finally {
            TenantUtils.clearIgnoreTenant();
        }
    }

    private Long resolveCampaignId(ExamSubmission submission) {
        if (submission.getEnrollmentId() == null) {
            throw new IllegalArgumentException("答卷未关联报名记录");
        }
        ExamEnrollment enrollment = enrollmentMapper.selectById(submission.getEnrollmentId());
        if (enrollment == null || enrollment.getCampaignId() == null) {
            throw new IllegalArgumentException("答卷未关联考试场次");
        }
        return enrollment.getCampaignId();
    }

    private void enqueue(ExamSubmission submission, Long campaignId) {
        ExamGradingTask task = new ExamGradingTask();
        task.setTenantId(submission.getTenantId());
        task.setCampaignId(campaignId);
        task.setSubmissionId(submission.getId());
        task.setStatus("QUEUED");
        task.setProgress(0);
        taskMapper.insert(task);
    }

    // ---------------------------------------------------------------- 总览

    @Override
    public GradingOverviewVO overview(Long campaignId) {
        TenantUtils.setIgnoreTenant();
        try {
            ExamCampaign campaign = campaignMapper.selectById(campaignId);
            if (campaign == null) {
                throw new IllegalArgumentException("考试场次不存在");
            }
            List<ExamQuestion> questions = questionMapper.selectList(new LambdaQueryWrapper<ExamQuestion>()
                    .eq(ExamQuestion::getCampaignId, campaignId)
                    .orderByAsc(ExamQuestion::getSortOrder)
                    .orderByAsc(ExamQuestion::getId));
            List<ExamEnrollment> enrollments = enrollmentMapper.selectList(new LambdaQueryWrapper<ExamEnrollment>()
                    .eq(ExamEnrollment::getCampaignId, campaignId)
                    .orderByAsc(ExamEnrollment::getId));
            List<Long> enrollmentIds = enrollments.stream().map(ExamEnrollment::getId)
                    .filter(Objects::nonNull).toList();
            List<ExamSubmission> submissions = enrollmentIds.isEmpty() ? List.of()
                    : submissionMapper.selectList(new LambdaQueryWrapper<ExamSubmission>()
                            .in(ExamSubmission::getEnrollmentId, enrollmentIds)
                            .ne(ExamSubmission::getStatus, "DRAFT")
                            .ne(ExamSubmission::getStatus, "NO_ANSWER")
                            .orderByDesc(ExamSubmission::getSubmittedAt)
                            .orderByDesc(ExamSubmission::getUpdatedAt))
                    .stream().filter(this::hasSubmittedWork).toList();

            List<Long> submissionIds = submissions.stream().map(ExamSubmission::getId)
                    .filter(Objects::nonNull).toList();
            Map<Long, ExamGradingTask> taskMap = latestTaskBySubmission(submissionIds);
            Map<Long, ExamGradingResult> resultMap = latestResultBySubmission(submissionIds);
            Map<Long, List<ExamSubmission>> byEnrollment = submissions.stream()
                    .filter(s -> s.getEnrollmentId() != null)
                    .collect(Collectors.groupingBy(ExamSubmission::getEnrollmentId, LinkedHashMap::new, Collectors.toList()));

            List<Long> accountIds = enrollments.stream().map(ExamEnrollment::getAccountId)
                    .filter(Objects::nonNull).distinct().toList();
            Map<Long, Account> accountMap = accountIds.isEmpty() ? Map.of() : accountMapper.selectList(
                            new LambdaQueryWrapper<Account>().in(Account::getId, accountIds)).stream()
                    .collect(Collectors.toMap(Account::getId, Function.identity(), (a, b) -> a));
            List<Long> tenantIds = enrollments.stream().map(ExamEnrollment::getTenantId)
                    .filter(Objects::nonNull).distinct().toList();
            Map<Long, Tenant> tenantMap = tenantIds.isEmpty() ? Map.of() : tenantMapper.selectList(
                            new LambdaQueryWrapper<Tenant>().in(Tenant::getId, tenantIds)).stream()
                    .collect(Collectors.toMap(Tenant::getId, Function.identity(), (a, b) -> a));
            List<Long> agentIds = submissions.stream().map(ExamSubmission::getAgentId)
                    .filter(Objects::nonNull).distinct().toList();
            Map<Long, AgentDefinition> agentMap = agentIds.isEmpty() ? Map.of() : agentDefinitionMapper.selectList(
                            new LambdaQueryWrapper<AgentDefinition>().in(AgentDefinition::getId, agentIds)).stream()
                    .collect(Collectors.toMap(AgentDefinition::getId, Function.identity(), (a, b) -> a));

            GradingOverviewVO vo = new GradingOverviewVO();
            vo.setCampaignId(campaignId);
            vo.setCampaignName(campaign.getName());
            vo.setTaskCounts(countTasks(campaignId));

            List<GradingCandidateRowVO> rows = new ArrayList<>();
            for (ExamEnrollment enrollment : enrollments) {
                GradingCandidateRowVO row = new GradingCandidateRowVO();
                row.setEnrollmentId(enrollment.getId());
                Account account = accountMap.get(enrollment.getAccountId());
                row.setCandidateName(candidateName(account, enrollment, campaign));
                row.setLoginName(account == null ? null : account.getUsername());
                row.setOrganization(enrollment.getOrganization());
                Tenant tenant = tenantMap.get(enrollment.getTenantId());
                row.setTenantName(tenant == null ? null : tenant.getName());
                List<ExamSubmission> enrollmentSubmissions = byEnrollment.getOrDefault(enrollment.getId(), List.of());
                row.setSubmittedCount(enrollmentSubmissions.size());
                row.setQuestionCount(Math.max(questions.size(), enrollmentSubmissions.size()));
                row.setQuestions(buildCells(questions, enrollmentSubmissions, taskMap, resultMap, agentMap));

                BigDecimal total = BigDecimal.ZERO;
                BigDecimal maxTotal = BigDecimal.ZERO;
                boolean any = false;
                for (GradingQuestionCellVO cell : row.getQuestions()) {
                    if (cell.getQuestionScore() != null) {
                        maxTotal = maxTotal.add(BigDecimal.valueOf(cell.getQuestionScore()));
                    }
                    if (Boolean.TRUE.equals(cell.getGraded()) && cell.getTotalScore() != null) {
                        any = true;
                        total = total.add(cell.getTotalScore());
                    }
                }
                row.setTotalScore(any ? total : null);
                row.setTotalMaxScore(maxTotal);
                rows.add(row);
            }
            vo.setRows(rows);
            return vo;
        } finally {
            TenantUtils.clearIgnoreTenant();
        }
    }

    private Map<String, Long> countTasks(Long campaignId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("QUEUED", 0L);
        counts.put("RUNNING", 0L);
        counts.put("SUCCEEDED", 0L);
        counts.put("FAILED", 0L);
        // 只统计每份答卷的最新一次任务，避免重跑后历史失败污染计数
        Map<Long, String> latestBySubmission = new LinkedHashMap<>();
        taskMapper.selectList(new LambdaQueryWrapper<ExamGradingTask>()
                        .eq(ExamGradingTask::getCampaignId, campaignId)
                        .orderByDesc(ExamGradingTask::getCreatedAt)
                        .orderByDesc(ExamGradingTask::getId))
                .forEach(task -> {
                    if (task.getSubmissionId() != null) {
                        latestBySubmission.putIfAbsent(task.getSubmissionId(), task.getStatus());
                    }
                });
        latestBySubmission.values().forEach(status -> counts.merge(status, 1L, Long::sum));
        return counts;
    }

    private List<GradingQuestionCellVO> buildCells(List<ExamQuestion> questions,
                                                   List<ExamSubmission> submissions,
                                                   Map<Long, ExamGradingTask> taskMap,
                                                   Map<Long, ExamGradingResult> resultMap,
                                                   Map<Long, AgentDefinition> agentMap) {
        List<GradingQuestionCellVO> cells = new ArrayList<>();
        Map<Long, ExamSubmission> byQuestion = new LinkedHashMap<>();
        List<ExamSubmission> unmatched = new ArrayList<>();
        for (ExamSubmission submission : submissions) {
            if (submission.getQuestionId() != null && questions.stream().anyMatch(q -> q.getId().equals(submission.getQuestionId()))) {
                byQuestion.merge(submission.getQuestionId(), submission,
                        (first, second) -> first); // 已按提交时间倒序，保留最新
            } else {
                unmatched.add(submission);
            }
        }
        int no = 1;
        for (ExamQuestion question : questions) {
            cells.add(buildCell(question, no++, byQuestion.get(question.getId()), taskMap, resultMap, agentMap));
        }
        for (ExamSubmission submission : unmatched) {
            GradingQuestionCellVO cell = buildCell(null, no++, submission, taskMap, resultMap, agentMap);
            cell.setQuestionTitle("未匹配题目");
            cells.add(cell);
        }
        return cells;
    }

    private GradingQuestionCellVO buildCell(ExamQuestion question, int no, ExamSubmission submission,
                                            Map<Long, ExamGradingTask> taskMap,
                                            Map<Long, ExamGradingResult> resultMap,
                                            Map<Long, AgentDefinition> agentMap) {
        GradingQuestionCellVO cell = new GradingQuestionCellVO();
        cell.setQuestionNo(no);
        if (question != null) {
            cell.setQuestionId(question.getId());
            cell.setQuestionTitle(question.getTitle());
            cell.setQuestionScore(question.getScore());
        }
        if (submission == null) {
            cell.setTaskStatus("未提交");
            cell.setGraded(false);
            return cell;
        }
        cell.setSubmissionId(submission.getId());
        cell.setSubmissionStatus(submission.getStatus());
        cell.setAgentId(submission.getAgentId());
        AgentDefinition agent = submission.getAgentId() == null ? null : agentMap.get(submission.getAgentId());
        cell.setAgentName(agent == null ? null : agent.getName());
        ExamGradingTask task = taskMap.get(submission.getId());
        if (task != null) {
            cell.setTaskStatus(task.getStatus());
            cell.setTaskErrorMessage(task.getErrorMessage());
        } else {
            cell.setTaskStatus("无任务");
        }
        ExamGradingResult result = resultMap.get(submission.getId());
        if (result != null) {
            cell.setGraded(true);
            cell.setTotalScore(result.getTotalScore());
            cell.setConfigMaxScore(result.getConfigMaxScore());
            cell.setDominanceStatus(result.getDominanceStatus());
            cell.setCapStatus(result.getCapStatus());
            cell.setConfidence(result.getConfidence());
            cell.setModelName(result.getModelName());
            cell.setGradedAt(result.getCreatedAt());
            cell.setIntegrityFlags(readFlags(result.getReportJson()));
        } else {
            cell.setGraded(false);
        }
        return cell;
    }

    // ---------------------------------------------------------------- 详情

    @Override
    public GradingDetailVO detail(Long submissionId) {
        TenantUtils.setIgnoreTenant();
        try {
            ExamSubmission submission = submissionMapper.selectById(submissionId);
            if (submission == null) {
                throw new IllegalArgumentException("答卷不存在");
            }
            ExamEnrollment enrollment = submission.getEnrollmentId() == null
                    ? null : enrollmentMapper.selectById(submission.getEnrollmentId());
            ExamCampaign campaign = enrollment == null ? null : campaignMapper.selectById(enrollment.getCampaignId());
            ExamQuestion question = submission.getQuestionId() == null
                    ? null : questionMapper.selectById(submission.getQuestionId());
            Account account = enrollment == null ? null : accountMapper.selectById(enrollment.getAccountId());
            AgentDefinition agent = submission.getAgentId() == null
                    ? null : agentDefinitionMapper.selectById(submission.getAgentId());
            ExamGradingTask task = taskMapper.selectOne(new LambdaQueryWrapper<ExamGradingTask>()
                    .eq(ExamGradingTask::getSubmissionId, submissionId)
                    .orderByDesc(ExamGradingTask::getCreatedAt)
                    .last("limit 1"));
            ExamGradingResult result = resultMapper.selectOne(new LambdaQueryWrapper<ExamGradingResult>()
                    .eq(ExamGradingResult::getSubmissionId, submissionId)
                    .orderByDesc(ExamGradingResult::getCreatedAt)
                    .last("limit 1"));

            GradingDetailVO vo = new GradingDetailVO();
            vo.setSubmissionId(submissionId);
            vo.setQuestionId(submission.getQuestionId());
            vo.setQuestionTitle(question == null ? null : question.getTitle());
            vo.setQuestionScore(question == null ? null : question.getScore());
            if (question != null) {
                try {
                    vo.setQuestionNo(resolveQuestionNo(question, submission));
                } catch (IllegalArgumentException ignored) {
                    vo.setQuestionNo(null);
                }
            }
            vo.setEnrollmentId(submission.getEnrollmentId());
            vo.setCandidateName(candidateName(account, enrollment, campaign));
            vo.setLoginName(account == null ? null : account.getUsername());
            vo.setAgentId(submission.getAgentId());
            vo.setAgentName(agent == null ? null : agent.getName());
            vo.setSubmissionStatus(submission.getStatus());
            vo.setTaskStatus(task == null ? "无任务" : task.getStatus());
            vo.setTaskErrorMessage(task == null ? null : task.getErrorMessage());
            if (result != null) {
                vo.setBaseScore(result.getBaseScore());
                vo.setCoefficient(result.getCoefficient());
                vo.setCapStatus(result.getCapStatus());
                vo.setCapRatio(result.getCapRatio());
                vo.setDominanceStatus(result.getDominanceStatus());
                vo.setTotalScore(result.getTotalScore());
                vo.setConfigMaxScore(result.getConfigMaxScore());
                vo.setConfidence(result.getConfidence());
                vo.setModelName(result.getModelName());
                vo.setRubricVersion(result.getRubricVersion());
                vo.setGradedAt(result.getCreatedAt());
                parseReport(result.getReportJson(), vo);
            }
            return vo;
        } finally {
            TenantUtils.clearIgnoreTenant();
        }
    }

    private void parseReport(String reportJson, GradingDetailVO vo) {
        if (reportJson == null || reportJson.isBlank()) {
            return;
        }
        try {
            JsonNode root = JSON.readTree(reportJson);
            vo.setChecklistHits(root.path("checklistHits").isInt() ? root.path("checklistHits").asInt() : null);
            vo.setChecklistDetail(readStrings(root.path("checklistDetail")));
            vo.setIntegrityFlags(readStrings(root.path("integrityFlags")));
            vo.setSummary(textValue(root.path("summary")));
            vo.setPlainComment(textValue(root.path("plainComment")));
            vo.setDeductionReason(textValue(root.path("deductionReason")));
            JsonNode deduction = root.path("deductionAmount");
            if (deduction.isNumber()) {
                vo.setDeductionAmount(new BigDecimal(deduction.asText()));
            }
            List<Map<String, String>> channels = new ArrayList<>();
            if (root.path("dominanceChannels").isArray()) {
                root.path("dominanceChannels").forEach(item -> {
                    Map<String, String> channel = new HashMap<>();
                    channel.put("channel", textValue(item.path("channel")));
                    channel.put("finding", textValue(item.path("finding")));
                    channel.put("evidence", textValue(item.path("evidence")));
                    channels.add(channel);
                });
            }
            vo.setDominanceChannels(channels);
            List<Map<String, Object>> items = new ArrayList<>();
            if (root.path("items").isArray()) {
                root.path("items").forEach(item -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("name", textValue(item.path("name")));
                    JsonNode score = item.path("score");
                    JsonNode max = item.path("max");
                    entry.put("score", score.isNumber() ? score.decimalValue() : textValue(score));
                    entry.put("max", max.isNumber() ? max.decimalValue() : textValue(max));
                    entry.put("evidence", textValue(item.path("evidence")));
                    items.add(entry);
                });
            }
            vo.setItems(items);
        } catch (Exception ex) {
            log.warn("AI 阅卷报告 JSON 解析失败 submissionId={}", vo.getSubmissionId(), ex);
        }
    }

    // ---------------------------------------------------------------- 采纳

    @Override
    public void adopt(Long submissionId, Long reviewerId) {
        TenantUtils.setIgnoreTenant();
        try {
            ExamSubmission submission = submissionMapper.selectById(submissionId);
            if (submission == null) {
                throw new IllegalArgumentException("答卷不存在");
            }
            ExamGradingResult result = resultMapper.selectOne(new LambdaQueryWrapper<ExamGradingResult>()
                    .eq(ExamGradingResult::getSubmissionId, submissionId)
                    .orderByDesc(ExamGradingResult::getCreatedAt)
                    .last("limit 1"));
            if (result == null || result.getTotalScore() == null) {
                throw new IllegalArgumentException("该答卷尚无 AI 阅卷结果");
            }
            ExamQuestion question = submission.getQuestionId() == null
                    ? null : questionMapper.selectById(submission.getQuestionId());
            BigDecimal configMax = result.getConfigMaxScore() == null || result.getConfigMaxScore().signum() <= 0
                    ? rubricStore.configMax(resolveQuestionNo(question, submission))
                    : result.getConfigMaxScore();
            int questionScore = question != null && question.getScore() != null && question.getScore() > 0
                    ? question.getScore() : configMax.intValue();
            int aiScore = result.getTotalScore()
                    .multiply(new BigDecimal(questionScore))
                    .divide(configMax, 0, RoundingMode.HALF_UP)
                    .intValue();

            String summary = textValue(parseJsonSafely(result.getReportJson()).path("plainComment"));
            if (summary == null || summary.isBlank()) {
                summary = textValue(parseJsonSafely(result.getReportJson()).path("summary"));
            }
            StringBuilder comment = new StringBuilder("AI 阅卷采纳（").append(result.getRubricVersion())
                    .append("，配置分 ").append(result.getTotalScore()).append("/").append(configMax.toPlainString())
                    .append("，归一化 ").append(result.getNormalizedScore()).append(" 分）");
            if (summary != null && !summary.isBlank()) {
                comment.append("：").append(summary);
            }
            String commentText = comment.length() > 900 ? comment.substring(0, 900) : comment.toString();
            // 标注来源为 AI 采纳，便于后续区分人工复核分与机评分。
            reviewerScoreService.saveFinalScore(submissionId, aiScore, commentText, reviewerId, "AI_ADOPT");
        } finally {
            TenantUtils.clearIgnoreTenant();
        }
    }

    private int resolveQuestionNo(ExamQuestion question, ExamSubmission submission) {
        if (question == null) {
            throw new IllegalArgumentException("答卷未关联题目，无法折算分数");
        }
        if (question.getSortOrder() != null && question.getSortOrder() >= 1 && question.getSortOrder() <= 4) {
            return question.getSortOrder();
        }
        List<ExamQuestion> questions = questionMapper.selectList(new LambdaQueryWrapper<ExamQuestion>()
                .eq(ExamQuestion::getCampaignId, question.getCampaignId())
                .orderByAsc(ExamQuestion::getSortOrder)
                .orderByAsc(ExamQuestion::getId));
        int index = 0;
        for (ExamQuestion item : questions) {
            index++;
            if (item.getId().equals(question.getId())) {
                break;
            }
        }
        if (index < 1 || index > 4) {
            throw new IllegalArgumentException("题目顺序超出量表范围（仅支持四题量表）");
        }
        return index;
    }

    // ---------------------------------------------------------------- 工具

    private Map<Long, ExamGradingTask> latestTaskBySubmission(List<Long> submissionIds) {
        if (submissionIds.isEmpty()) {
            return Map.of();
        }
        return taskMapper.selectList(new LambdaQueryWrapper<ExamGradingTask>()
                        .in(ExamGradingTask::getSubmissionId, submissionIds)
                        .orderByDesc(ExamGradingTask::getCreatedAt))
                .stream()
                .filter(task -> task.getSubmissionId() != null)
                .collect(Collectors.toMap(ExamGradingTask::getSubmissionId, Function.identity(),
                        (first, second) -> first, LinkedHashMap::new));
    }

    private Map<Long, ExamGradingResult> latestResultBySubmission(List<Long> submissionIds) {
        if (submissionIds.isEmpty()) {
            return Map.of();
        }
        return resultMapper.selectList(new LambdaQueryWrapper<ExamGradingResult>()
                        .in(ExamGradingResult::getSubmissionId, submissionIds)
                        .orderByDesc(ExamGradingResult::getCreatedAt))
                .stream()
                .filter(result -> result.getSubmissionId() != null)
                .collect(Collectors.toMap(ExamGradingResult::getSubmissionId, Function.identity(),
                        (first, second) -> first, LinkedHashMap::new));
    }

    /** 考生姓名：账号昵称优先（与复核成绩口径一致），回退报名名/账号名。 */
    private String candidateName(Account account, ExamEnrollment enrollment, ExamCampaign campaign) {
        String nickname = account == null ? null : account.getNickname();
        String campaignName = campaign == null ? null : campaign.getName();
        if (sameText(nickname, campaignName)) {
            nickname = null;
        }
        String enrollmentName = enrollment == null ? null : enrollment.getName();
        if (sameText(enrollmentName, campaignName)) {
            enrollmentName = null;
        }
        String first = firstNonBlank(nickname, enrollmentName);
        return first != null ? first : (account == null ? null : account.getUsername());
    }

    private boolean sameText(String a, String b) {
        return a != null && b != null && !a.isBlank() && a.trim().equals(b.trim());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private boolean hasSubmittedWork(ExamSubmission submission) {
        if (submission == null) {
            return false;
        }
        if ("NO_ANSWER".equals(submission.getStatus())) {
            return false;
        }
        return submission.getAgentId() != null
                || notBlank(textField(submission.getFormJson(), "work", "title"))
                || notBlank(textField(submission.getFormJson(), "work", "scene"))
                || notBlank(textField(submission.getFormJson(), "work", "functionDescription"));
    }

    private String textField(String formJson, String section, String field) {
        JsonNode node = parseJsonSafely(formJson).path(section).path(field);
        return node.isValueNode() && !node.isNull() ? node.asText("") : null;
    }

    private JsonNode parseJsonSafely(String json) {
        if (json == null || json.isBlank()) {
            return JSON.createObjectNode();
        }
        try {
            JsonNode node = JSON.readTree(json);
            return node == null ? JSON.createObjectNode() : node;
        } catch (Exception ignored) {
            return JSON.createObjectNode();
        }
    }

    private List<String> readFlags(String reportJson) {
        JsonNode flags = parseJsonSafely(reportJson).path("integrityFlags");
        return readStrings(flags);
    }

    private List<String> readStrings(JsonNode value) {
        List<String> result = new ArrayList<>();
        if (value.isArray()) {
            value.forEach(item -> {
                if (item.isValueNode() && !item.isNull() && !item.asText().isBlank()) {
                    result.add(item.asText().trim());
                }
            });
        }
        return result;
    }

    private String textValue(JsonNode node) {
        if (node == null || !node.isValueNode() || node.isNull()) {
            return null;
        }
        String value = node.asText("").trim();
        return value.isEmpty() ? null : value;
    }
}
