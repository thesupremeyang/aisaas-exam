package com.hxh.apboa.assessment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hxh.apboa.assessment.dto.GradingOutput;
import com.hxh.apboa.assessment.mapper.ExamGradingResultMapper;
import com.hxh.apboa.assessment.mapper.ExamGradingTaskMapper;
import com.hxh.apboa.assessment.mapper.ExamQuestionMapper;
import com.hxh.apboa.assessment.mapper.ExamSubmissionMapper;
import com.hxh.apboa.common.entity.ExamGradingResult;
import com.hxh.apboa.common.entity.ExamGradingTask;
import com.hxh.apboa.common.entity.ExamQuestion;
import com.hxh.apboa.common.entity.ExamSubmission;
import com.hxh.apboa.common.util.TenantUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 阅卷任务轮询 Worker（配置评分）：QUEUED → RUNNING → 证据组装 → 模型评分 → 结果落库 → SUCCEEDED。
 * 与旧 AI 智评（AssessmentTaskWorker）完全独立：旧管线读自述表单且已屏蔽；本 Worker 读真实配置。
 * 未配置模型（assessment.grading.*）或开关关闭时保持队列原样。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExamGradingTaskWorker {

    private final ExamGradingTaskMapper taskMapper;
    private final ExamGradingResultMapper resultMapper;
    private final ExamSubmissionMapper submissionMapper;
    private final ExamQuestionMapper questionMapper;
    private final ExamGradingSnapshotService snapshotService;
    private final ExamGradingLlmClient llmClient;
    private final ExamGradingRubricStore rubricStore;
    private final ObjectMapper objectMapper;

    @Value("${assessment.grading.enabled:false}")
    private boolean gradingEnabled;

    @Scheduled(fixedDelayString = "${assessment.grading.worker-fixed-delay-ms:3000}")
    public void poll() {
        if (!gradingEnabled) {
            return;
        }
        TenantUtils.setIgnoreTenant();
        ExamGradingTask task;
        try {
            task = taskMapper.selectOne(new LambdaQueryWrapper<ExamGradingTask>()
                    .eq(ExamGradingTask::getStatus, "QUEUED")
                    .orderByAsc(ExamGradingTask::getCreatedAt)
                    .last("limit 1"));
        } finally {
            TenantUtils.clearIgnoreTenant();
        }
        if (task != null) {
            process(task);
        }
    }

    public void process(ExamGradingTask task) {
        TenantUtils.setCurrentTenant(task.getTenantId(), null);
        TenantUtils.clearIgnoreTenant();
        try {
            task.setStatus("RUNNING");
            task.setProgress(10);
            task.setStartedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            try {
                doProcess(task);
                task.setStatus("SUCCEEDED");
                task.setProgress(100);
                task.setFinishedAt(LocalDateTime.now());
                taskMapper.updateById(task);
            } catch (RuntimeException ex) {
                fail(task, ex);
            }
        } finally {
            TenantUtils.clear();
        }
    }

    private void doProcess(ExamGradingTask task) {
        ExamSubmission submission = submissionMapper.selectById(task.getSubmissionId());
        if (submission == null) {
            throw new IllegalArgumentException("答卷不存在");
        }
        ExamQuestion question = submission.getQuestionId() == null
                ? null : questionMapper.selectById(submission.getQuestionId());
        int questionNo = resolveQuestionNo(question);
        // 评分满分与题目设置的分值联动：优先取 exam_question.score，未配置时回退量表内置分值
        BigDecimal configMax = question != null && question.getScore() != null && question.getScore() > 0
                ? BigDecimal.valueOf(question.getScore())
                : rubricStore.configMax(questionNo);

        setProgress(task, 35);
        ExamGradingSnapshotService.GradingSnapshot snapshot = snapshotService.build(submission, question);
        setProgress(task, 55);

        ExamGradingResult result = new ExamGradingResult();
        result.setTenantId(submission.getTenantId());
        result.setSubmissionId(submission.getId());
        result.setTaskId(task.getId());
        result.setAgentId(snapshot.agentId());
        result.setQuestionId(submission.getQuestionId());
        result.setConfigMaxScore(configMax);
        result.setRubricVersion(rubricStore.version());
        result.setPromptVersion("grading-v1");
        result.setModelName(llmClient.modelName());

        boolean noAgent = snapshot.agentId() == null || hasAgentMissingFlag(snapshot.integrityFlags());
        if (noAgent) {
            // 无智能体/智能体已删除：无可评配置，直接落零分结果，不调用模型
            result.setBaseScore(BigDecimal.ZERO);
            result.setCoefficient(BigDecimal.ONE);
            result.setDominanceStatus(null);
            result.setTotalScore(BigDecimal.ZERO);
            result.setNormalizedScore(BigDecimal.ZERO);
            result.setConfidence("LOW");
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("items", List.of());
            report.put("checklistHits", 0);
            report.put("checklistDetail", List.of());
            report.put("dominanceChannels", List.of());
            report.put("dominanceStatus", null);
            report.put("deductionAmount", 0);
            report.put("deductionReason", "");
            report.put("summary", "未关联智能体或智能体已不存在，无真实配置可评，按\"无法验证\"处理记 0 分。");
            report.put("integrityFlags", snapshot.integrityFlags());
            result.setReportJson(toJson(report));
        } else {
            GradingOutput output = gradeWithRetry(questionNo, configMax, snapshot.evidenceDoc());
            List<String> mergedFlags = new ArrayList<>(snapshot.integrityFlags());
            output.integrityFlags().forEach(flag -> {
                if (!mergedFlags.contains(flag)) {
                    mergedFlags.add(flag);
                }
            });
            result.setBaseScore(output.baseScore());
            result.setCoefficient(output.coefficient());
            result.setCapRatio(output.capRatio());
            result.setCapStatus(output.capStatus());
            result.setDominanceStatus(output.dominanceStatus());
            result.setTotalScore(output.totalScore());
            result.setNormalizedScore(output.totalScore()
                    .multiply(new BigDecimal("100"))
                    .divide(configMax, 1, RoundingMode.HALF_UP));
            result.setConfidence(output.confidence());

            Map<String, Object> report = new LinkedHashMap<>();
            List<Map<String, Object>> items = new ArrayList<>();
            output.items().forEach(item -> {
                Map<String, Object> itemMap = new LinkedHashMap<>();
                itemMap.put("name", item.name());
                itemMap.put("score", item.score());
                itemMap.put("max", item.max());
                itemMap.put("evidence", item.evidence());
                items.add(itemMap);
            });
            report.put("items", items);
            report.put("checklistHits", output.checklistHits());
            report.put("checklistDetail", output.checklistDetail());
            report.put("dominanceStatus", output.dominanceStatus());
            List<Map<String, String>> channels = new ArrayList<>();
            output.dominanceChannels().forEach(channel -> {
                Map<String, String> channelMap = new LinkedHashMap<>();
                channelMap.put("channel", channel.channel());
                channelMap.put("finding", channel.finding());
                channelMap.put("evidence", channel.evidence());
                channels.add(channelMap);
            });
            report.put("dominanceChannels", channels);
            report.put("capStatus", output.capStatus());
            report.put("capRatio", output.capRatio());
            report.put("deductionAmount", output.deductionAmount());
            report.put("deductionReason", output.deductionReason());
            report.put("plainComment", output.plainComment());
            report.put("summary", output.summary());
            report.put("integrityFlags", mergedFlags);
            result.setReportJson(toJson(report));
        }
        resultMapper.insert(result);
        setProgress(task, 95);
    }

    private GradingOutput gradeWithRetry(int questionNo, BigDecimal configMax, String evidenceDoc) {
        try {
            return llmClient.grade(questionNo, configMax, evidenceDoc);
        } catch (RuntimeAssessmentException ex) {
            if (ex.kind() == RuntimeAssessmentException.Kind.INVALID_RESPONSE) {
                log.info("AI 阅卷输出校验失败，重试一次：{}", ex.getMessage());
                return llmClient.grade(questionNo, configMax, evidenceDoc);
            }
            throw ex;
        }
    }

    private boolean hasAgentMissingFlag(List<String> flags) {
        return flags != null && flags.stream().anyMatch(flag ->
                flag != null && (flag.contains("未关联智能体") || flag.contains("智能体不存在")));
    }

    private int resolveQuestionNo(ExamQuestion question) {
        if (question == null) {
            throw new IllegalArgumentException("答卷未关联题目，无法匹配量表");
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
            throw new IllegalArgumentException("题目顺序超出四题量表范围，无法评分");
        }
        return index;
    }

    private void setProgress(ExamGradingTask task, int progress) {
        task.setProgress(progress);
        taskMapper.updateById(task);
    }

    private void fail(ExamGradingTask task, RuntimeException ex) {
        task.setStatus("FAILED");
        task.setErrorCode(errorCode(ex));
        String message = ex.getMessage() == null || ex.getMessage().isBlank()
                ? "AI 阅卷失败" : ex.getMessage();
        task.setErrorMessage(message.length() > 1000 ? message.substring(0, 1000) : message);
        task.setFinishedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        log.warn("AI 阅卷任务失败 taskId={}, submissionId={}, errorCode={}, message={}",
                task.getId(), task.getSubmissionId(), task.getErrorCode(), task.getErrorMessage(), ex);
    }

    private String errorCode(RuntimeException ex) {
        if (ex instanceof RuntimeAssessmentException runtimeException) {
            return switch (runtimeException.kind()) {
                case TIMEOUT -> "GRADING_TIMEOUT";
                case CONNECTION -> "GRADING_CONNECTION";
                case HTTP_STATUS -> "GRADING_HTTP_STATUS";
                case INVALID_RESPONSE -> "GRADING_INVALID_RESPONSE";
            };
        }
        return "GRADING_FAILED";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("阅卷报告序列化失败", ex);
        }
    }
}
