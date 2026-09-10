package com.hxh.apboa.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hxh.apboa.account.mapper.AccountMapper;
import com.hxh.apboa.account.mapper.TenantMapper;
import com.hxh.apboa.agent.mapper.AgentDefinitionMapper;
import com.hxh.apboa.assessment.dto.ExamScoreRowVO;
import com.hxh.apboa.assessment.dto.ExamSubmissionDetailVO;
import com.hxh.apboa.assessment.mapper.AssessmentReportMapper;
import com.hxh.apboa.assessment.mapper.AssessmentReviewMapper;
import com.hxh.apboa.assessment.mapper.ExamCampaignMapper;
import com.hxh.apboa.assessment.mapper.ExamEnrollmentMapper;
import com.hxh.apboa.assessment.mapper.ExamQuestionMapper;
import com.hxh.apboa.assessment.mapper.ExamSubmissionMapper;
import com.hxh.apboa.assessment.service.AssessmentRubric;
import com.hxh.apboa.assessment.service.ExamScoreConverter;
import com.hxh.apboa.assessment.service.ExamReviewerScoreService;
import com.hxh.apboa.common.entity.Account;
import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.entity.AssessmentReport;
import com.hxh.apboa.common.entity.AssessmentReview;
import com.hxh.apboa.common.entity.ExamCampaign;
import com.hxh.apboa.common.entity.ExamEnrollment;
import com.hxh.apboa.common.entity.ExamQuestion;
import com.hxh.apboa.common.entity.ExamSubmission;
import com.hxh.apboa.common.entity.Tenant;
import com.hxh.apboa.common.util.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExamReviewerScoreServiceImpl implements ExamReviewerScoreService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final double FINAL_SCORE_WEIGHT = 0.8d;

    private final ExamSubmissionMapper submissionMapper;
    private final ExamEnrollmentMapper enrollmentMapper;
    private final ExamCampaignMapper campaignMapper;
    private final AssessmentReportMapper reportMapper;
    private final AssessmentReviewMapper reviewMapper;
    private final AccountMapper accountMapper;
    private final TenantMapper tenantMapper;
    private final AgentDefinitionMapper agentDefinitionMapper;
    private final ExamQuestionMapper questionMapper;

    @Override
    public List<ExamScoreRowVO> listScores(Long campaignId, String nameFilter) {
        // 成绩复核面向全部学员，跨租户查询；已提交作品：排除 DRAFT（未提交）
        TenantUtils.setIgnoreTenant();
        try {
            List<Long> campaignEnrollmentIds = campaignId == null ? List.of() : enrollmentMapper.selectList(
                            new LambdaQueryWrapper<ExamEnrollment>()
                                    .eq(ExamEnrollment::getCampaignId, campaignId))
                    .stream().map(ExamEnrollment::getId).filter(Objects::nonNull).toList();
            if (campaignId != null && campaignEnrollmentIds.isEmpty()) {
                return List.of();
            }

            LambdaQueryWrapper<ExamSubmission> submissionQuery = new LambdaQueryWrapper<ExamSubmission>()
                    .ne(ExamSubmission::getStatus, "DRAFT")
                    .orderByDesc(ExamSubmission::getSubmittedAt)
                    .orderByDesc(ExamSubmission::getUpdatedAt);
            if (campaignId != null) {
                submissionQuery.in(ExamSubmission::getEnrollmentId, campaignEnrollmentIds);
            }
            List<ExamSubmission> submissions = submissionMapper.selectList(submissionQuery)
                    .stream()
                    // 超时任务会把未填写的草稿转为 TIMEOUT_SUBMITTED；这类记录不是有效作品，不能出现在成绩复核作品列表中。
                    .filter(this::hasSubmittedWork)
                    .toList();
            if (submissions.isEmpty()) {
                return List.of();
            }

            Map<Long, List<ExamSubmission>> submissionsByEnrollment = new LinkedHashMap<>();
            submissions.forEach(submission -> {
                if (submission.getEnrollmentId() != null) {
                    submissionsByEnrollment.computeIfAbsent(submission.getEnrollmentId(), ignored -> new ArrayList<>())
                            .add(submission);
                }
            });
            if (submissionsByEnrollment.isEmpty()) {
                return List.of();
            }

            List<Long> enrollmentIds = new ArrayList<>(submissionsByEnrollment.keySet());
            Map<Long, ExamEnrollment> enrollmentMap = enrollmentMapper.selectList(
                            new LambdaQueryWrapper<ExamEnrollment>().in(ExamEnrollment::getId, enrollmentIds))
                    .stream().collect(Collectors.toMap(ExamEnrollment::getId, Function.identity()));

            List<Long> campaignIds = enrollmentMap.values().stream()
                    .map(ExamEnrollment::getCampaignId).filter(Objects::nonNull).distinct().toList();
            Map<Long, ExamCampaign> campaignMap = campaignIds.isEmpty() ? Map.of() : campaignMapper.selectList(
                            new LambdaQueryWrapper<ExamCampaign>().in(ExamCampaign::getId, campaignIds))
                    .stream().collect(Collectors.toMap(ExamCampaign::getId, Function.identity()));
            Map<Long, List<ExamQuestion>> questionsByCampaign = loadQuestionsByCampaign(campaignIds);

            List<Long> accountIds = enrollmentMap.values().stream()
                    .map(ExamEnrollment::getAccountId).filter(Objects::nonNull).distinct().toList();
            Map<Long, Account> accountMap = accountIds.isEmpty() ? Map.of() : accountMapper.selectList(
                            new LambdaQueryWrapper<Account>().in(Account::getId, accountIds))
                    .stream().collect(Collectors.toMap(Account::getId, Function.identity()));

            List<Long> tenantIds = enrollmentMap.values().stream()
                    .map(ExamEnrollment::getTenantId).filter(Objects::nonNull).distinct().toList();
            Map<Long, Tenant> tenantMap = tenantIds.isEmpty() ? Map.of() : tenantMapper.selectList(
                            new LambdaQueryWrapper<Tenant>().in(Tenant::getId, tenantIds))
                    .stream().collect(Collectors.toMap(Tenant::getId, Function.identity()));

            List<Long> agentIds = submissions.stream()
                    .map(ExamSubmission::getAgentId).filter(Objects::nonNull).distinct().toList();
            Map<Long, AgentDefinition> agentMap = agentIds.isEmpty() ? Map.of() : agentDefinitionMapper.selectList(
                            new LambdaQueryWrapper<AgentDefinition>().in(AgentDefinition::getId, agentIds))
                    .stream().collect(Collectors.toMap(AgentDefinition::getId, Function.identity()));

            List<Long> submissionIds = submissions.stream().map(ExamSubmission::getId)
                    .filter(Objects::nonNull).toList();
            Map<Long, AssessmentReport> reportMap = loadLatestReports(submissionIds);
            Map<Long, AssessmentReview> reviewMap = loadLatestReviews(submissionIds);

            return submissionsByEnrollment.entrySet().stream().map(entry -> {
                Long enrollmentId = entry.getKey();
                List<ExamSubmission> enrollmentSubmissions = entry.getValue();
                ExamEnrollment enrollment = enrollmentMap.get(enrollmentId);
                ExamCampaign campaign = enrollment == null ? null : campaignMap.get(enrollment.getCampaignId());
                Account account = enrollment == null ? null : accountMap.get(enrollment.getAccountId());
                Tenant tenant = enrollment == null ? null : tenantMap.get(enrollment.getTenantId());
                List<ExamQuestion> questions = campaign == null
                        ? List.of() : questionsByCampaign.getOrDefault(campaign.getId(), List.of());
                Map<Long, ExamQuestion> questionMap = questions.stream()
                        .filter(question -> question.getId() != null)
                        .collect(Collectors.toMap(ExamQuestion::getId, Function.identity(), (first, ignored) -> first));
                int rawScoreLimit = rubricLimit(campaign);
                int totalLimit = totalQuestionLimit(enrollmentSubmissions, questions, questionMap, rawScoreLimit);
                SubmissionForm identity = mergeForms(enrollmentSubmissions);
                ExamSubmission firstSubmission = enrollmentSubmissions.get(0);

                ExamScoreRowVO vo = new ExamScoreRowVO();
                vo.setEnrollmentId(enrollmentId);
                // 保留首条提交 ID 兼容旧调用方；列表行本身按 enrollmentId 唯一。
                vo.setSubmissionId(firstSubmission.getId());
                vo.setCandidateName(candidateName(identity.applicantName(), enrollment, account, campaign));
                vo.setIdentityNumber(identity.identityNumber());
                vo.setPhone(identity.phone());
                vo.setOrganization(firstNonBlank(identity.organization(),
                        enrollment == null ? null : enrollment.getOrganization()));
                vo.setLoginName(account == null ? null : account.getUsername());
                vo.setTenantName(tenant == null ? null : tenant.getName());
                vo.setWorkType(firstSubmission.getWorkType());
                vo.setWorkTypeLabel(workTypeLabel(firstSubmission.getWorkType()));
                vo.setSubmissionStatus(firstSubmission.getStatus());
                vo.setSubmittedAt(firstSubmission.getSubmittedAt());
                vo.setWorkName(identity.workTitle());
                vo.setDemoUrl(identity.demoUrl());
                vo.setQuestionCount(Math.max(questions.size(), enrollmentSubmissions.size()));
                vo.setSubmittedQuestionCount(enrollmentSubmissions.size());
                vo.setTotalLimit(totalLimit);
                vo.setDemoCount((int) enrollmentSubmissions.stream()
                        .map(ExamSubmission::getFormJson)
                        .map(this::parseForm)
                        .filter(form -> !isBlank(form.demoUrl()))
                        .count());

                int aiScoredCount = 0;
                int aiTotal = 0;
                int reviewedCount = 0;
                int finalTotal = 0;
                int finalScoredCount = 0;
                AssessmentReport representativeReport = null;
                AssessmentReview representativeReview = null;
                for (ExamSubmission submission : enrollmentSubmissions) {
                    ExamQuestion question = questionMap.get(submission.getQuestionId());
                    int questionLimit = questionLimit(question, rawScoreLimit);
                    AssessmentReport report = reportMap.get(submission.getId());
                    if (report != null && report.getTotalScore() != null) {
                        aiScoredCount++;
                        aiTotal += convertedScore(report.getTotalScore(), rawScoreLimit, questionLimit);
                        if (representativeReport == null) representativeReport = report;
                    }
                    AssessmentReview review = reviewMap.get(submission.getId());
                    if (isReviewed(review)) {
                        reviewedCount++;
                        if (representativeReview == null) representativeReview = review;
                    }
                    Integer finalScore = finalScoreForQuestion(review, rawScoreLimit, questionLimit);
                    if (finalScore != null) {
                        finalScoredCount++;
                        finalTotal += finalScore;
                    }
                }
                vo.setAiScoredQuestionCount(aiScoredCount);
                vo.setAiTotalScore(aiScoredCount == 0 ? null : aiTotal);
                vo.setReviewedQuestionCount(reviewedCount);
                vo.setFinalScore(finalScoredCount == 0 ? null : finalTotal);
                vo.setReviewed(!enrollmentSubmissions.isEmpty() && reviewedCount == enrollmentSubmissions.size());
                if (representativeReport != null) {
                    vo.setConfidence(representativeReport.getConfidence());
                    vo.setModelName(representativeReport.getModelName());
                    vo.setAiScoredAt(representativeReport.getCreatedAt());
                }
                if (representativeReview != null) {
                    vo.setReviewComment(representativeReview.getComment());
                }
                if (isBlank(vo.getWorkName())) {
                    AgentDefinition agent = firstSubmission.getAgentId() == null
                            ? null : agentMap.get(firstSubmission.getAgentId());
                    if (agent != null) vo.setWorkName(agent.getName());
                }
                // 按姓名筛选，筛选单位是学员总览行而不是单个作品。
                if (nameFilter != null && !nameFilter.isBlank()
                        && (vo.getCandidateName() == null || !vo.getCandidateName().contains(nameFilter.trim()))) {
                    return null;
                }
                return vo;
            }).filter(v -> v != null).toList();
        } finally {
            TenantUtils.clearIgnoreTenant();
        }
    }

    @Override
    public byte[] exportScores(Long campaignId) {
        if (campaignId == null) {
            throw new IllegalArgumentException("请选择考试场次");
        }
        TenantUtils.setIgnoreTenant();
        try {
            ExamCampaign campaign = campaignMapper.selectById(campaignId);
            if (campaign == null) {
                throw new IllegalArgumentException("考试场次不存在");
            }

            List<ExamQuestion> questions = questionMapper.selectList(
                    new LambdaQueryWrapper<ExamQuestion>()
                            .eq(ExamQuestion::getCampaignId, campaignId)
                            .orderByAsc(ExamQuestion::getSortOrder)
                            .orderByAsc(ExamQuestion::getId));
            List<ExamEnrollment> enrollments = enrollmentMapper.selectList(
                    new LambdaQueryWrapper<ExamEnrollment>()
                            .eq(ExamEnrollment::getCampaignId, campaignId)
                            .orderByAsc(ExamEnrollment::getId));
            List<Long> enrollmentIds = enrollments.stream()
                    .map(ExamEnrollment::getId).filter(Objects::nonNull).toList();
            List<ExamSubmission> submissions = enrollmentIds.isEmpty() ? List.of() : submissionMapper.selectList(
                            new LambdaQueryWrapper<ExamSubmission>()
                                    .in(ExamSubmission::getEnrollmentId, enrollmentIds)
                                    .ne(ExamSubmission::getStatus, "DRAFT")
                                    .orderByDesc(ExamSubmission::getSubmittedAt)
                                    .orderByDesc(ExamSubmission::getUpdatedAt))
                    .stream()
                    .filter(this::hasSubmittedWork)
                    .toList();
            Map<Long, List<ExamSubmission>> submissionsByEnrollment = submissions.stream()
                    .filter(submission -> submission.getEnrollmentId() != null)
                    .collect(Collectors.groupingBy(ExamSubmission::getEnrollmentId,
                            LinkedHashMap::new, Collectors.toList()));

            List<Long> accountIds = enrollments.stream()
                    .map(ExamEnrollment::getAccountId).filter(Objects::nonNull).distinct().toList();
            Map<Long, Account> accountMap = accountIds.isEmpty() ? Map.of() : accountMapper.selectList(
                            new LambdaQueryWrapper<Account>().in(Account::getId, accountIds))
                    .stream().collect(Collectors.toMap(Account::getId, Function.identity()));

            List<Long> submissionIds = submissions.stream().map(ExamSubmission::getId)
                    .filter(Objects::nonNull).toList();
            Map<Long, AssessmentReport> reportMap = loadLatestReports(submissionIds);
            Map<Long, AssessmentReview> reviewMap = loadLatestReviews(submissionIds);
            return buildScoreWorkbook(campaign, questions, enrollments, submissionsByEnrollment,
                    accountMap, reportMap, reviewMap);
        } finally {
            TenantUtils.clearIgnoreTenant();
        }
    }

    private byte[] buildScoreWorkbook(ExamCampaign campaign,
                                      List<ExamQuestion> questions,
                                      List<ExamEnrollment> enrollments,
                                      Map<Long, List<ExamSubmission>> submissionsByEnrollment,
                                      Map<Long, Account> accountMap,
                                      Map<Long, AssessmentReport> reportMap,
                                      Map<Long, AssessmentReview> reviewMap) {
        int rawScoreLimit = rubricLimit(campaign);
        List<String> headers = new ArrayList<>();
        headers.add("学员姓名");
        headers.add("学员账号");
        for (int i = 0; i < questions.size(); i++) {
            ExamQuestion question = questions.get(i);
            String questionLabel = "第" + questionNumber(question, questions, i + 1) + "题";
            if (!isBlank(question.getTitle())) {
                questionLabel += "（" + question.getTitle().trim() + "）";
            }
            headers.add(questionLabel + " AI得分");
            headers.add(questionLabel + " 复核得分");
        }
        headers.add("AI总分");
        headers.add("复核总分");
        headers.add("最终成绩（80%）");

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("成绩汇总");
            sheet.createFreezePane(2, 1);
            sheet.setDefaultRowHeightInPoints(20);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setWrapText(true);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);

            CellStyle textStyle = workbook.createCellStyle();
            textStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle scoreStyle = workbook.createCellStyle();
            scoreStyle.setAlignment(HorizontalAlignment.CENTER);
            scoreStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            scoreStyle.setDataFormat(workbook.createDataFormat().getFormat("0"));

            CellStyle weightedScoreStyle = workbook.createCellStyle();
            weightedScoreStyle.cloneStyleFrom(scoreStyle);
            weightedScoreStyle.setDataFormat(workbook.createDataFormat().getFormat("0.0"));

            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(32);
            for (int i = 0; i < headers.size(); i++) {
                setTextCell(headerRow, i, headers.get(i), headerStyle);
            }

            for (int rowIndex = 0; rowIndex < enrollments.size(); rowIndex++) {
                ExamEnrollment enrollment = enrollments.get(rowIndex);
                List<ExamSubmission> studentSubmissions = submissionsByEnrollment.getOrDefault(
                        enrollment.getId(), List.of());
                Map<Long, ExamSubmission> submissionByQuestion = latestSubmissionsByQuestion(studentSubmissions);
                Account account = accountMap.get(enrollment.getAccountId());
                SubmissionForm identity = mergeForms(studentSubmissions);

                Row row = sheet.createRow(rowIndex + 1);
                setTextCell(row, 0, candidateName(identity.applicantName(), enrollment, account, campaign), textStyle);
                setTextCell(row, 1, account == null ? null : account.getUsername(), textStyle);

                int column = 2;
                int aiTotal = 0;
                int finalTotal = 0;
                boolean hasAiScore = false;
                boolean hasFinalScore = false;
                for (ExamQuestion question : questions) {
                    ExamSubmission submission = submissionByQuestion.get(question.getId());
                    AssessmentReport report = submission == null ? null : reportMap.get(submission.getId());
                    AssessmentReview review = submission == null ? null : reviewMap.get(submission.getId());
                    int questionScore = questionLimit(question, rawScoreLimit);
                    Integer aiScore = report == null || report.getTotalScore() == null ? null
                            : convertedScore(report.getTotalScore(), rawScoreLimit, questionScore);
                    Integer finalScore = finalScoreForQuestion(review, rawScoreLimit, questionScore);
                    setScoreCell(row, column++, aiScore, scoreStyle);
                    setScoreCell(row, column++, finalScore, scoreStyle);
                    if (aiScore != null) {
                        hasAiScore = true;
                        aiTotal += aiScore;
                    }
                    if (finalScore != null) {
                        hasFinalScore = true;
                        finalTotal += finalScore;
                    }
                }
                setScoreCell(row, column++, hasAiScore ? aiTotal : null, scoreStyle);
                setScoreCell(row, column++, hasFinalScore ? finalTotal : null, scoreStyle);
                setWeightedScoreCell(row, column,
                        hasFinalScore ? finalTotal * FINAL_SCORE_WEIGHT : null, weightedScoreStyle);
            }

            for (int column = 0; column < headers.size(); column++) {
                int width = column < 2 ? 22 : 16;
                sheet.setColumnWidth(column, width * 256);
            }
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, enrollments.size()),
                    0, headers.size() - 1));
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("导出成绩失败", exception);
        }
    }

    private Map<Long, ExamSubmission> latestSubmissionsByQuestion(List<ExamSubmission> submissions) {
        return submissions.stream()
                .filter(submission -> submission.getQuestionId() != null)
                .collect(Collectors.toMap(ExamSubmission::getQuestionId, Function.identity(),
                        (first, ignored) -> first, LinkedHashMap::new));
    }

    private void setTextCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private void setScoreCell(Row row, int column, Integer value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value != null) {
            cell.setCellValue(value);
        }
        cell.setCellStyle(style);
    }

    private void setWeightedScoreCell(Row row, int column, Double value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value != null) {
            cell.setCellValue(value);
        }
        cell.setCellStyle(style);
    }

    @Override
    public ExamSubmissionDetailVO getSubmissionDetail(Long submissionId) {
        TenantUtils.setIgnoreTenant();
        try {
            ExamSubmission submission = submissionMapper.selectById(submissionId);
            if (submission == null) {
                throw new IllegalArgumentException("作品提交记录不存在");
            }

            ExamEnrollment enrollment = enrollmentMapper.selectById(submission.getEnrollmentId());
            ExamCampaign campaign = enrollment == null ? null : campaignMapper.selectById(enrollment.getCampaignId());
            Account account = enrollment == null ? null : accountMapper.selectById(enrollment.getAccountId());
            Tenant tenant = enrollment == null ? null : tenantMapper.selectById(enrollment.getTenantId());
            AgentDefinition agent = submission.getAgentId() == null
                    ? null : agentDefinitionMapper.selectById(submission.getAgentId());
            AssessmentReport report = reportMapper.selectOne(new LambdaQueryWrapper<AssessmentReport>()
                    .eq(AssessmentReport::getSubmissionId, submissionId)
                    .orderByDesc(AssessmentReport::getCreatedAt)
                    .last("limit 1"));
            AssessmentReview review = reviewMapper.selectOne(new LambdaQueryWrapper<AssessmentReview>()
                    .eq(AssessmentReview::getSubmissionId, submissionId)
                    .orderByDesc(AssessmentReview::getUpdatedAt)
                    .last("limit 1"));

            ExamQuestion question = null;
            if (campaign != null && submission.getQuestionId() != null) {
                ExamQuestion candidateQuestion = questionMapper.selectById(submission.getQuestionId());
                if (candidateQuestion != null && Objects.equals(campaign.getId(), candidateQuestion.getCampaignId())) {
                    question = candidateQuestion;
                }
            }
            int rawScoreLimit = rubricLimit(campaign);
            int questionScore = questionLimit(question, rawScoreLimit);

            JsonNode form = parseFormJson(submission.getFormJson());
            JsonNode applicant = form.path("applicant");
            JsonNode work = form.path("work");
            JsonNode materials = form.path("materials");

            ExamSubmissionDetailVO vo = new ExamSubmissionDetailVO();
            vo.setEnrollmentId(submission.getEnrollmentId());
            vo.setSubmissionId(submission.getId());
            vo.setLoginName(account == null ? null : account.getUsername());
            vo.setEmail(account == null ? null : account.getEmail());
            vo.setTenantName(tenant == null ? null : tenant.getName());
            vo.setCandidateName(candidateName(text(applicant, "name"), enrollment, account, campaign));
            vo.setIdentityNumber(text(applicant, "identityNumber"));
            vo.setPhone(text(applicant, "phone"));
            vo.setOrganization(firstNonBlank(text(applicant, "organization"),
                    enrollment == null ? null : enrollment.getOrganization()));
            vo.setWorkType(submission.getWorkType());
            vo.setWorkTypeLabel(workTypeLabel(submission.getWorkType()));
            vo.setSubmissionStatus(submission.getStatus());
            vo.setSubmittedAt(submission.getSubmittedAt());
            vo.setAgentId(submission.getAgentId());
            vo.setAgentName(agent == null ? null : agent.getName());
            vo.setWorkName(firstNonBlank(text(work, "title"), vo.getAgentName()));
            vo.setScene(text(work, "scene"));
            vo.setFunctionDescription(text(work, "functionDescription"));
            vo.setInnovationDescription(text(work, "innovationDescription"));
            vo.setPromotionDescription(text(work, "promotionDescription"));
            vo.setDataDescription(text(work, "dataDescription"));
            vo.setRuntimeEnvironment(text(work, "runtimeEnvironment"));
            vo.setTeamDescription(text(work, "teamDescription"));
            vo.setTechnicalDescription(text(work, "technicalDescription"));
            vo.setHasSkill(work.path("hasSkill").asBoolean(false));
            vo.setHasMcp(work.path("hasMcp").asBoolean(false));
            vo.setHasKnowledge(work.path("hasKnowledge").asBoolean(false));
            vo.setDemoUrl(text(materials, "demoUrl"));
            vo.setHasDemo(work.path("hasDemo").asBoolean(false) || !isBlank(vo.getDemoUrl()));
            vo.setMaterialDescription(firstNonBlank(text(materials, "materialDescription"),
                    text(materials, "description"), text(form, "materialDescription")));
            vo.setScreenshotFiles(readFiles(materials.path("screenshotFiles")));
            vo.setVideoFiles(readFiles(materials.path("videoFiles")));
            vo.setPackageFiles(readFiles(materials.path("packageFiles")));
            vo.setSupplementaryFiles(readFiles(materials.path("supplementaryFiles")));
            vo.setQuestionCount(1);
            vo.setSubmittedQuestionCount(1);
            vo.setTotalLimit(questionScore);
            if (report != null) {
                vo.setAiTotalScore(report.getTotalScore() == null ? null
                        : ExamScoreConverter.toQuestionScore(report.getTotalScore(), rawScoreLimit, questionScore));
                vo.setAiScoredQuestionCount(report.getTotalScore() == null ? 0 : 1);
                vo.setConfidence(report.getConfidence());
                vo.setModelName(report.getModelName());
                vo.setAiScoredAt(report.getCreatedAt());
                JsonNode reportJson = parseFormJson(report.getReportJson());
                vo.setDimensionScores(readIntMap(reportJson.path("dimensionScores")));
                vo.setConclusion(text(reportJson, "conclusion"));
                vo.setEvidence(readEvidence(reportJson.path("evidence")));
                vo.setRisks(readTextList(reportJson.path("risks")));
                vo.setSuggestions(readTextList(reportJson.path("suggestions")));
            }
            if (review != null) {
                vo.setFinalScore(finalScoreForQuestion(review, rawScoreLimit, questionScore));
                vo.setReviewedQuestionCount(isReviewed(review) ? 1 : 0);
                vo.setReviewComment(review.getComment());
                vo.setReviewed(review.getTotalScore() != null || !isBlank(review.getComment()));
            }
            vo.setQuestions(List.of(buildQuestionScore(submission,
                    questionNumber(question, question == null ? List.of() : List.of(question), 1),
                    question, agent, report, review, rawScoreLimit)));
            return vo;
        } finally {
            TenantUtils.clearIgnoreTenant();
        }
    }

    @Override
    public ExamSubmissionDetailVO getEnrollmentDetail(Long enrollmentId) {
        TenantUtils.setIgnoreTenant();
        try {
            ExamEnrollment enrollment = enrollmentMapper.selectById(enrollmentId);
            if (enrollment == null) {
                throw new IllegalArgumentException("考试报名记录不存在");
            }
            ExamCampaign campaign = campaignMapper.selectById(enrollment.getCampaignId());
            Account account = accountMapper.selectById(enrollment.getAccountId());
            Tenant tenant = tenantMapper.selectById(enrollment.getTenantId());
            List<ExamSubmission> submissions = submissionMapper.selectList(
                    new LambdaQueryWrapper<ExamSubmission>()
                            .eq(ExamSubmission::getEnrollmentId, enrollmentId)
                            .ne(ExamSubmission::getStatus, "DRAFT")
                            .orderByDesc(ExamSubmission::getSubmittedAt)
                            .orderByDesc(ExamSubmission::getUpdatedAt))
                    .stream()
                    .filter(this::hasSubmittedWork)
                    .toList();
            List<ExamQuestion> questions = campaign == null ? List.of() : questionMapper.selectList(
                    new LambdaQueryWrapper<ExamQuestion>()
                            .eq(ExamQuestion::getCampaignId, campaign.getId())
                            .orderByAsc(ExamQuestion::getSortOrder)
                            .orderByAsc(ExamQuestion::getId));
            Map<Long, ExamQuestion> questionMap = questions.stream()
                    .filter(question -> question.getId() != null)
                    .collect(Collectors.toMap(ExamQuestion::getId, Function.identity(), (first, ignored) -> first));
            List<Long> submissionIds = submissions.stream().map(ExamSubmission::getId)
                    .filter(Objects::nonNull).toList();
            List<Long> agentIds = submissions.stream().map(ExamSubmission::getAgentId)
                    .filter(Objects::nonNull).distinct().toList();
            Map<Long, AgentDefinition> agentMap = agentIds.isEmpty() ? Map.of() : agentDefinitionMapper.selectList(
                            new LambdaQueryWrapper<AgentDefinition>().in(AgentDefinition::getId, agentIds))
                    .stream().collect(Collectors.toMap(AgentDefinition::getId, Function.identity()));
            Map<Long, AssessmentReport> reportMap = loadLatestReports(submissionIds);
            Map<Long, AssessmentReview> reviewMap = loadLatestReviews(submissionIds);
            int rawScoreLimit = rubricLimit(campaign);
            int totalLimit = totalQuestionLimit(submissions, questions, questionMap, rawScoreLimit);

            List<ExamSubmission> orderedSubmissions = new ArrayList<>(submissions);
            orderedSubmissions.sort(Comparator
                    .comparingInt((ExamSubmission submission) -> questionOrder(
                            questionMap.get(submission.getQuestionId()), Integer.MAX_VALUE))
                    .thenComparing(ExamSubmission::getSubmittedAt,
                            Comparator.nullsLast(Comparator.reverseOrder())));

            SubmissionForm identity = mergeForms(submissions);
            ExamSubmissionDetailVO vo = new ExamSubmissionDetailVO();
            vo.setEnrollmentId(enrollmentId);
            vo.setSubmissionId(submissions.isEmpty() ? null : submissions.get(0).getId());
            vo.setLoginName(account == null ? null : account.getUsername());
            vo.setEmail(account == null ? null : account.getEmail());
            vo.setTenantName(tenant == null ? null : tenant.getName());
            vo.setCandidateName(candidateName(identity.applicantName(), enrollment, account, campaign));
            vo.setIdentityNumber(identity.identityNumber());
            vo.setPhone(identity.phone());
            vo.setOrganization(firstNonBlank(identity.organization(), enrollment.getOrganization()));
            vo.setQuestionCount(Math.max(questions.size(), submissions.size()));
            vo.setSubmittedQuestionCount(submissions.size());
            vo.setTotalLimit(totalLimit);
            vo.setDemoCount((int) submissions.stream()
                    .map(ExamSubmission::getFormJson)
                    .map(this::parseForm)
                    .filter(form -> !isBlank(form.demoUrl()))
                    .count());
            if (!submissions.isEmpty()) {
                vo.setSubmissionStatus(submissions.get(0).getStatus());
                vo.setSubmittedAt(submissions.get(0).getSubmittedAt());
            }

            List<ExamSubmissionDetailVO.QuestionScoreVO> questionDetails = new ArrayList<>();
            for (int i = 0; i < orderedSubmissions.size(); i++) {
                ExamSubmission submission = orderedSubmissions.get(i);
                ExamQuestion question = questionMap.get(submission.getQuestionId());
                questionDetails.add(buildQuestionScore(submission,
                        questionNumber(question, questions, i + 1), question,
                        submission.getAgentId() == null ? null : agentMap.get(submission.getAgentId()),
                        reportMap.get(submission.getId()), reviewMap.get(submission.getId()), rawScoreLimit));
            }
            vo.setQuestions(questionDetails);

            int aiScoredCount = (int) questionDetails.stream()
                    .filter(question -> question.getAiRawScore() != null).count();
            int aiTotal = questionDetails.stream()
                    .map(ExamSubmissionDetailVO.QuestionScoreVO::getAiScore)
                    .filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
            int reviewedCount = (int) questionDetails.stream()
                    .filter(ExamSubmissionDetailVO.QuestionScoreVO::isReviewed).count();
            int finalScoredCount = (int) questionDetails.stream()
                    .filter(question -> question.getFinalScore() != null).count();
            int finalTotal = questionDetails.stream()
                    .map(ExamSubmissionDetailVO.QuestionScoreVO::getFinalScore)
                    .filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
            vo.setAiScoredQuestionCount(aiScoredCount);
            vo.setAiTotalScore(aiScoredCount == 0 ? null : aiTotal);
            vo.setReviewedQuestionCount(reviewedCount);
            vo.setFinalScore(finalScoredCount == 0 ? null : finalTotal);
            vo.setReviewed(!submissions.isEmpty() && reviewedCount == submissions.size());
            if (!questionDetails.isEmpty()) {
                ExamSubmissionDetailVO.QuestionScoreVO first = questionDetails.get(0);
                vo.setWorkName(first.getWorkName());
                vo.setAgentId(first.getAgentId());
                vo.setAgentName(first.getAgentName());
                vo.setDemoUrl(first.getDemoUrl());
            }
            return vo;
        } finally {
            TenantUtils.clearIgnoreTenant();
        }
    }

    private Map<Long, List<ExamQuestion>> loadQuestionsByCampaign(List<Long> campaignIds) {
        if (campaignIds.isEmpty()) return Map.of();
        return questionMapper.selectList(new LambdaQueryWrapper<ExamQuestion>()
                        .in(ExamQuestion::getCampaignId, campaignIds)
                        .orderByAsc(ExamQuestion::getSortOrder)
                        .orderByAsc(ExamQuestion::getId))
                .stream()
                .filter(question -> question.getCampaignId() != null)
                .collect(Collectors.groupingBy(ExamQuestion::getCampaignId, LinkedHashMap::new, Collectors.toList()));
    }

    private Map<Long, AssessmentReport> loadLatestReports(List<Long> submissionIds) {
        if (submissionIds.isEmpty()) return Map.of();
        return reportMapper.selectList(new LambdaQueryWrapper<AssessmentReport>()
                        .in(AssessmentReport::getSubmissionId, submissionIds)
                        .orderByDesc(AssessmentReport::getCreatedAt))
                .stream()
                .filter(report -> report.getSubmissionId() != null)
                .collect(Collectors.toMap(AssessmentReport::getSubmissionId, Function.identity(),
                        (first, ignored) -> first, LinkedHashMap::new));
    }

    private Map<Long, AssessmentReview> loadLatestReviews(List<Long> submissionIds) {
        if (submissionIds.isEmpty()) return Map.of();
        return reviewMapper.selectList(new LambdaQueryWrapper<AssessmentReview>()
                        .in(AssessmentReview::getSubmissionId, submissionIds)
                        .orderByDesc(AssessmentReview::getUpdatedAt)
                        .orderByDesc(AssessmentReview::getCreatedAt))
                .stream()
                .filter(review -> review.getSubmissionId() != null)
                .collect(Collectors.toMap(AssessmentReview::getSubmissionId, Function.identity(),
                        (first, ignored) -> first, LinkedHashMap::new));
    }

    private SubmissionForm mergeForms(List<ExamSubmission> submissions) {
        SubmissionForm merged = new SubmissionForm(null, null, null, null, null, null);
        for (ExamSubmission submission : submissions) {
            SubmissionForm form = parseForm(submission.getFormJson());
            merged = new SubmissionForm(
                    firstNonBlank(merged.applicantName(), form.applicantName()),
                    firstNonBlank(merged.identityNumber(), form.identityNumber()),
                    firstNonBlank(merged.phone(), form.phone()),
                    firstNonBlank(merged.organization(), form.organization()),
                    firstNonBlank(merged.workTitle(), form.workTitle()),
                    firstNonBlank(merged.demoUrl(), form.demoUrl()));
        }
        return merged;
    }

    private int rubricLimit(ExamCampaign campaign) {
        return campaign == null ? AssessmentRubric.fromCampaignConfig(null).totalScore()
                : AssessmentRubric.fromCampaignConfig(campaign.getConfigJson()).totalScore();
    }

    private int questionLimit(ExamQuestion question, int rawScoreLimit) {
        return question != null && question.getScore() != null && question.getScore() > 0
                ? question.getScore() : rawScoreLimit;
    }

    private int totalQuestionLimit(List<ExamSubmission> submissions, List<ExamQuestion> questions,
                                   Map<Long, ExamQuestion> questionMap, int rawScoreLimit) {
        if (questions.isEmpty()) {
            return rawScoreLimit * submissions.size();
        }
        int total = questions.stream()
                .mapToInt(question -> questionLimit(question, rawScoreLimit))
                .sum();
        long unmatched = submissions.stream()
                .filter(submission -> submission.getQuestionId() == null
                        || !questionMap.containsKey(submission.getQuestionId()))
                .count();
        return total + Math.toIntExact(unmatched * rawScoreLimit);
    }

    private int convertedScore(Integer rawScore, int rawScoreLimit, int questionScore) {
        return ExamScoreConverter.toQuestionScore(rawScore, rawScoreLimit, questionScore);
    }

    /** 新版管理员复核直接保存本题分；未标记的历史复核记录仍按量表原始分兼容折算。 */
    private Integer finalScoreForQuestion(AssessmentReview review, int rawScoreLimit, int questionScore) {
        if (review == null || review.getTotalScore() == null) return null;
        return isQuestionScoreReview(review)
                ? review.getTotalScore()
                : convertedScore(review.getTotalScore(), rawScoreLimit, questionScore);
    }

    private boolean isQuestionScoreReview(AssessmentReview review) {
        return review != null
                && "QUESTION_SCORE".equals(parseFormJson(review.getChecklistJson()).path("scoreMode").asText());
    }

    private boolean isReviewed(AssessmentReview review) {
        return review != null && (review.getTotalScore() != null || !isBlank(review.getComment()));
    }

    private int questionOrder(ExamQuestion question, int fallback) {
        return question != null && question.getSortOrder() != null && question.getSortOrder() > 0
                ? question.getSortOrder() : fallback;
    }

    private int questionNumber(ExamQuestion question, List<ExamQuestion> questions, int fallback) {
        if (question != null && question.getSortOrder() != null && question.getSortOrder() > 0) {
            return question.getSortOrder();
        }
        if (question != null && question.getId() != null) {
            for (int i = 0; i < questions.size(); i++) {
                if (question.getId().equals(questions.get(i).getId())) return i + 1;
            }
        }
        return fallback;
    }

    private ExamSubmissionDetailVO.QuestionScoreVO buildQuestionScore(
            ExamSubmission submission,
            int questionNo,
            ExamQuestion question,
            AgentDefinition agent,
            AssessmentReport report,
            AssessmentReview review,
            int rawScoreLimit) {
        JsonNode form = parseFormJson(submission.getFormJson());
        JsonNode work = form.path("work");
        JsonNode materials = form.path("materials");
        String agentName = agent == null ? null : agent.getName();
        String demoUrl = text(materials, "demoUrl");

        ExamSubmissionDetailVO.QuestionScoreVO vo = new ExamSubmissionDetailVO.QuestionScoreVO();
        vo.setQuestionNo(questionNo);
        vo.setQuestionId(submission.getQuestionId());
        vo.setQuestionTitle(firstNonBlank(question == null ? null : question.getTitle(), "未关联试题"));
        vo.setQuestionScenario(question == null ? null : question.getTaskScenario());
        vo.setQuestionScore(questionLimit(question, rawScoreLimit));
        vo.setSubmissionId(submission.getId());
        vo.setAgentId(submission.getAgentId());
        vo.setAgentName(agentName);
        vo.setWorkName(firstNonBlank(text(work, "title"), agentName));
        vo.setWorkType(submission.getWorkType());
        vo.setWorkTypeLabel(workTypeLabel(submission.getWorkType()));
        vo.setSubmissionStatus(submission.getStatus());
        vo.setSubmittedAt(submission.getSubmittedAt());
        vo.setScene(text(work, "scene"));
        vo.setFunctionDescription(text(work, "functionDescription"));
        vo.setInnovationDescription(text(work, "innovationDescription"));
        vo.setPromotionDescription(text(work, "promotionDescription"));
        vo.setDataDescription(text(work, "dataDescription"));
        vo.setRuntimeEnvironment(text(work, "runtimeEnvironment"));
        vo.setTeamDescription(text(work, "teamDescription"));
        vo.setTechnicalDescription(text(work, "technicalDescription"));
        vo.setHasSkill(work.path("hasSkill").asBoolean(false));
        vo.setHasMcp(work.path("hasMcp").asBoolean(false));
        vo.setHasKnowledge(work.path("hasKnowledge").asBoolean(false));
        vo.setDemoUrl(demoUrl);
        vo.setHasDemo(work.path("hasDemo").asBoolean(false) || !isBlank(demoUrl));
        vo.setMaterialDescription(firstNonBlank(text(materials, "materialDescription"),
                text(materials, "description"), text(form, "materialDescription")));
        vo.setScreenshotFiles(readFiles(materials.path("screenshotFiles")));
        vo.setVideoFiles(readFiles(materials.path("videoFiles")));
        vo.setPackageFiles(readFiles(materials.path("packageFiles")));
        vo.setSupplementaryFiles(readFiles(materials.path("supplementaryFiles")));
        vo.setRawScoreLimit(rawScoreLimit);

        if (report != null) {
            vo.setAiRawScore(report.getTotalScore());
            vo.setAiScore(report.getTotalScore() == null ? null
                    : convertedScore(report.getTotalScore(), rawScoreLimit, vo.getQuestionScore()));
            vo.setConfidence(report.getConfidence());
            vo.setModelName(report.getModelName());
            vo.setAiScoredAt(report.getCreatedAt());
            JsonNode reportJson = parseFormJson(report.getReportJson());
            vo.setDimensionScores(readIntMap(reportJson.path("dimensionScores")));
            vo.setConclusion(text(reportJson, "conclusion"));
            vo.setEvidence(readEvidence(reportJson.path("evidence")));
            vo.setRisks(readTextList(reportJson.path("risks")));
            vo.setSuggestions(readTextList(reportJson.path("suggestions")));
        }
        if (review != null) {
            if (!isQuestionScoreReview(review)) {
                vo.setFinalRawScore(review.getTotalScore());
                vo.setReviewDimensionScores(readIntMap(parseFormJson(review.getScoresJson())));
            }
            vo.setFinalScore(finalScoreForQuestion(review, rawScoreLimit, vo.getQuestionScore()));
            vo.setReviewComment(review.getComment());
            vo.setReviewed(isReviewed(review));
        }
        return vo;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) return value;
        }
        return null;
    }

    /** 报名记录的历史数据可能把班次名称写入 name，优先使用账号昵称作为学员姓名。 */
    private String candidateName(String applicantName, ExamEnrollment enrollment,
                                 Account account, ExamCampaign campaign) {
        String enrollmentName = enrollment == null ? null : enrollment.getName();
        String campaignName = campaign == null ? null : campaign.getName();
        // 历史提交表单也可能把考试名称写入 applicant.name，不能让它进入姓名列。
        if (sameText(applicantName, campaignName)) {
            applicantName = null;
        }
        if (sameText(enrollmentName, campaignName)) {
            enrollmentName = null;
        }
        String nickname = account == null ? null : account.getNickname();
        if (sameText(nickname, campaignName)) {
            nickname = null;
        }
        // 历史报名导入可能写入其他班次名称；账号昵称是账号资料中的考生姓名，优先使用。
        return firstNonBlank(nickname, applicantName, enrollmentName,
                account == null ? null : account.getUsername());
    }

    private boolean sameText(String first, String second) {
        return !isBlank(first) && !isBlank(second) && first.trim().equals(second.trim());
    }

    /**
     * 判断一条已提交记录是否确实包含作品内容。
     * 正常提交必须关联平台智能体；兼容历史数据时，也接受填写过核心作品字段但智能体已被删除的记录。
     */
    private boolean hasSubmittedWork(ExamSubmission submission) {
        if (submission == null) return false;
        if (submission.getAgentId() != null) return true;
        JsonNode work = parseFormJson(submission.getFormJson()).path("work");
        return !isBlank(text(work, "title"))
                || !isBlank(text(work, "scene"))
                || !isBlank(text(work, "functionDescription"));
    }

    /** 从提交材料 form_json 中提取考生身份与作品字段。 */
    private SubmissionForm parseForm(String formJson) {
        if (isBlank(formJson)) {
            return new SubmissionForm(null, null, null, null, null, null);
        }
        try {
            JsonNode root = JSON.readTree(formJson);
            JsonNode applicant = root.path("applicant");
            String applicantName = text(applicant, "name");
            String identityNumber = text(applicant, "identityNumber");
            String phone = text(applicant, "phone");
            String organization = text(applicant, "organization");
            String workTitle = text(root.path("work"), "title");
            String demoUrl = text(root.path("materials"), "demoUrl");
            return new SubmissionForm(applicantName, identityNumber, phone, organization, workTitle, demoUrl);
        } catch (Exception ignored) {
            return new SubmissionForm(null, null, null, null, null, null);
        }
    }

    private JsonNode parseFormJson(String json) {
        if (isBlank(json)) return JSON.createObjectNode();
        try {
            JsonNode root = JSON.readTree(json);
            return root == null ? JSON.createObjectNode() : root;
        } catch (Exception ignored) {
            return JSON.createObjectNode();
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        JsonNode value = node.path(field);
        if (!value.isValueNode() || value.isNull()) return null;
        String text = value.asText(null);
        return isBlank(text) ? null : text.trim();
    }

    private List<ExamSubmissionDetailVO.SubmissionFileVO> readFiles(JsonNode value) {
        if (value == null || !value.isArray()) return List.of();
        List<ExamSubmissionDetailVO.SubmissionFileVO> files = new ArrayList<>();
        value.forEach(item -> {
            ExamSubmissionDetailVO.SubmissionFileVO file = new ExamSubmissionDetailVO.SubmissionFileVO();
            if (item.isTextual()) {
                file.setName(item.asText());
                file.setUrl(item.asText());
            } else if (item.isObject()) {
                String id = text(item, "id");
                file.setUrl(firstNonBlank(text(item, "url"),
                        id == null ? null : "/api/attach/download/" + id));
                file.setName(firstNonBlank(text(item, "name"), text(item, "originalName"), id));
                file.setSize(longValue(item, "size", "attachSize"));
            }
            if (!isBlank(file.getName()) || !isBlank(file.getUrl())) files.add(file);
        });
        return files;
    }

    private Long longValue(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.canConvertToLong()) return value.asLong();
        }
        return null;
    }

    private Map<String, Integer> readIntMap(JsonNode value) {
        if (value == null || !value.isObject()) return Map.of();
        Map<String, Integer> values = new LinkedHashMap<>();
        value.fields().forEachRemaining(entry -> {
            if (entry.getValue().canConvertToInt()) values.put(entry.getKey(), entry.getValue().asInt());
        });
        return values;
    }

    private List<ExamSubmissionDetailVO.SubmissionDimEvidenceVO> readEvidence(JsonNode value) {
        if (value == null || !value.isArray()) return List.of();
        List<ExamSubmissionDetailVO.SubmissionDimEvidenceVO> result = new ArrayList<>();
        value.forEach(item -> {
            if (!item.isObject()) return;
            String content = text(item, "content");
            if (isBlank(content)) return;
            ExamSubmissionDetailVO.SubmissionDimEvidenceVO evidence =
                    new ExamSubmissionDetailVO.SubmissionDimEvidenceVO();
            evidence.setDimension(firstNonBlank(text(item, "dimension"), "综合材料"));
            evidence.setContent(content);
            result.add(evidence);
        });
        return result;
    }

    private List<String> readTextList(JsonNode value) {
        if (value == null || !value.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        value.forEach(item -> {
            if (item.isValueNode() && !isBlank(item.asText())) result.add(item.asText().trim());
        });
        return result;
    }

    private record SubmissionForm(
            String applicantName,
            String identityNumber,
            String phone,
            String organization,
            String workTitle,
            String demoUrl
    ) {
    }

    private String workTypeLabel(String workType) {
        if (workType == null) return workType;
        return switch (workType.toUpperCase()) {
            case "AGENT" -> "智能体应用";
            case "DOCUMENT" -> "文档方案";
            case "CODE" -> "代码作品";
            case "PRESENTATION" -> "演示文稿";
            default -> workType;
        };
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveFinalScore(Long submissionId, Integer finalScore, String comment, Long reviewerId) {
        // 成绩复核面向平台管理员，提交记录属于考生租户；保存时必须与详情查询一样跨租户处理。
        TenantUtils.setIgnoreTenant();
        try {
            ExamSubmission submission = submissionMapper.selectById(submissionId);
            if (submission == null) {
                throw new IllegalArgumentException("作品提交记录不存在");
            }
            ExamEnrollment enrollment = enrollmentMapper.selectById(submission.getEnrollmentId());
            ExamCampaign campaign = enrollment == null ? null : campaignMapper.selectById(enrollment.getCampaignId());
            int limit = rubricLimit(campaign);
            ExamQuestion question = null;
            if (campaign != null && submission.getQuestionId() != null) {
                ExamQuestion candidateQuestion = questionMapper.selectById(submission.getQuestionId());
                if (candidateQuestion != null && Objects.equals(campaign.getId(), candidateQuestion.getCampaignId())) {
                    question = candidateQuestion;
                }
            }
            int questionScore = questionLimit(question, limit);
            if (finalScore == null || finalScore < 0 || finalScore > questionScore) {
                throw new IllegalArgumentException("最终得分必须在 0 到 " + questionScore + " 之间");
            }
            AssessmentReview review = reviewMapper.selectOne(new LambdaQueryWrapper<AssessmentReview>()
                    .eq(AssessmentReview::getSubmissionId, submissionId));
            if (review == null) {
                review = new AssessmentReview();
                review.setSubmissionId(submissionId);
                // 新建复核记录跟随作品租户，避免后续按租户查询时丢失。
                review.setTenantId(submission.getTenantId());
            }
            review.setReviewerAccountId(reviewerId);
            review.setTotalScore(finalScore);
            review.setChecklistJson("{\"scoreMode\":\"QUESTION_SCORE\"}");
            review.setComment(comment == null ? "" : comment.trim());
            review.setStatus("PUBLISHED");
            if (review.getId() == null) {
                reviewMapper.insert(review);
            } else {
                reviewMapper.updateById(review);
            }
        } finally {
            TenantUtils.clearIgnoreTenant();
        }
    }
}
