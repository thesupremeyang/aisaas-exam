package com.hxh.apboa.assessment.service.impl;

import com.hxh.apboa.assessment.mapper.AssessmentReportMapper;
import com.hxh.apboa.assessment.mapper.AssessmentReviewMapper;
import com.hxh.apboa.assessment.mapper.ExamCampaignMapper;
import com.hxh.apboa.assessment.mapper.ExamEnrollmentMapper;
import com.hxh.apboa.assessment.mapper.ExamQuestionMapper;
import com.hxh.apboa.assessment.mapper.ExamSubmissionMapper;
import com.hxh.apboa.account.mapper.AccountMapper;
import com.hxh.apboa.agent.mapper.AgentDefinitionMapper;
import com.hxh.apboa.account.mapper.TenantMapper;
import com.hxh.apboa.common.entity.Account;
import com.hxh.apboa.common.entity.AssessmentReport;
import com.hxh.apboa.common.entity.AssessmentReview;
import com.hxh.apboa.common.entity.ExamCampaign;
import com.hxh.apboa.common.entity.ExamEnrollment;
import com.hxh.apboa.common.entity.ExamQuestion;
import com.hxh.apboa.common.entity.ExamSubmission;
import com.hxh.apboa.common.entity.Tenant;
import com.hxh.apboa.common.util.TenantUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamReviewerScoreServiceImplTest {
    @Mock
    private ExamSubmissionMapper submissionMapper;
    @Mock
    private ExamEnrollmentMapper enrollmentMapper;
    @Mock
    private ExamCampaignMapper campaignMapper;
    @Mock
    private AssessmentReportMapper reportMapper;
    @Mock
    private AssessmentReviewMapper reviewMapper;
    @Mock
    private AccountMapper accountMapper;
    @Mock
    private TenantMapper tenantMapper;
    @Mock
    private AgentDefinitionMapper agentDefinitionMapper;
    @Mock
    private ExamQuestionMapper questionMapper;

    @InjectMocks
    private ExamReviewerScoreServiceImpl service;

    @Test
    void enrollmentDetailUsesConfiguredQuestionScores() {
        ExamEnrollment enrollment = new ExamEnrollment();
        enrollment.setId(1L);
        enrollment.setCampaignId(2L);
        enrollment.setAccountId(3L);
        enrollment.setTenantId(4L);

        ExamCampaign campaign = new ExamCampaign();
        campaign.setId(2L);
        campaign.setConfigJson(null);

        ExamQuestion firstQuestion = question(11L, 25, 1);
        ExamQuestion secondQuestion = question(12L, 35, 2);
        ExamSubmission firstSubmission = submission(21L, 11L, 101L);
        ExamSubmission secondSubmission = submission(22L, 12L, 102L);
        AssessmentReport firstReport = report(21L, 60);
        AssessmentReport secondReport = report(22L, 60);
        AssessmentReview firstReview = directReview(21L, 24);
        AssessmentReview secondReview = directReview(22L, 33);

        when(enrollmentMapper.selectById(1L)).thenReturn(enrollment);
        when(campaignMapper.selectById(2L)).thenReturn(campaign);
        when(accountMapper.selectById(3L)).thenReturn(null);
        when(tenantMapper.selectById(4L)).thenReturn(null);
        when(submissionMapper.selectList(any())).thenReturn(List.of(firstSubmission, secondSubmission));
        when(questionMapper.selectList(any())).thenReturn(List.of(firstQuestion, secondQuestion));
        when(agentDefinitionMapper.selectList(any())).thenReturn(List.of());
        when(reportMapper.selectList(any())).thenReturn(List.of(firstReport, secondReport));
        when(reviewMapper.selectList(any())).thenReturn(List.of(firstReview, secondReview));

        var detail = service.getEnrollmentDetail(1L);

        assertEquals(60, detail.getTotalLimit());
        assertEquals(36, detail.getAiTotalScore());
        assertEquals(25, detail.getQuestions().get(0).getQuestionScore());
        assertEquals(15, detail.getQuestions().get(0).getAiScore());
        assertEquals(35, detail.getQuestions().get(1).getQuestionScore());
        assertEquals(21, detail.getQuestions().get(1).getAiScore());
        assertEquals(57, detail.getFinalScore());
        assertEquals(24, detail.getQuestions().get(0).getFinalScore());
        assertEquals(33, detail.getQuestions().get(1).getFinalScore());
        assertNull(detail.getQuestions().get(0).getFinalRawScore());
    }

    @Test
    void exportUsesAccountNameWhenEnrollmentContainsHistoricalCampaignName() throws Exception {
        ExamCampaign campaign = new ExamCampaign();
        campaign.setId(2L);
        campaign.setName("2026国产大模型应用与AI编程智能体开发考试");

        ExamEnrollment enrollment = new ExamEnrollment();
        enrollment.setId(1L);
        enrollment.setCampaignId(2L);
        enrollment.setAccountId(3L);
        enrollment.setName("2026 智能体开发专题班");

        ExamSubmission submission = submission(21L, 11L, 101L);
        submission.setFormJson("{\"applicant\":{\"name\":\"2026 智能体开发专题班\"}}");
        Account account = new Account();
        account.setId(3L);
        account.setNickname("宋婵");
        account.setUsername("jnsc91361943");

        when(campaignMapper.selectById(2L)).thenReturn(campaign);
        when(questionMapper.selectList(any())).thenReturn(List.of(question(11L, 25, 1)));
        when(enrollmentMapper.selectList(any())).thenReturn(List.of(enrollment));
        when(submissionMapper.selectList(any())).thenReturn(List.of(submission));
        when(accountMapper.selectList(any())).thenReturn(List.of(account));
        when(reportMapper.selectList(any())).thenReturn(List.of());
        when(reviewMapper.selectList(any())).thenReturn(List.of());

        byte[] exported = service.exportScores(2L);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(exported))) {
            assertEquals("宋婵", workbook.getSheet("成绩汇总")
                    .getRow(1).getCell(0).getStringCellValue());
        }
    }

    @Test
    void exportAddsWeightedFinalScore() throws Exception {
        ExamCampaign campaign = new ExamCampaign();
        campaign.setId(2L);
        campaign.setName("2026 智能体开发专题班");

        ExamEnrollment enrollment = new ExamEnrollment();
        enrollment.setId(1L);
        enrollment.setCampaignId(2L);
        enrollment.setAccountId(3L);
        enrollment.setName("测试考生");

        ExamQuestion question = question(11L, 20, 1);
        ExamSubmission submission = submission(21L, 11L, 101L);
        AssessmentReview review = directReview(21L, 17);
        Account account = new Account();
        account.setId(3L);
        account.setUsername("test-candidate");
        account.setNickname("测试考生");

        when(campaignMapper.selectById(2L)).thenReturn(campaign);
        when(questionMapper.selectList(any())).thenReturn(List.of(question));
        when(enrollmentMapper.selectList(any())).thenReturn(List.of(enrollment));
        when(submissionMapper.selectList(any())).thenReturn(List.of(submission));
        when(accountMapper.selectList(any())).thenReturn(List.of(account));
        when(reportMapper.selectList(any())).thenReturn(List.of());
        when(reviewMapper.selectList(any())).thenReturn(List.of(review));

        byte[] exported = service.exportScores(2L);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(exported))) {
            var sheet = workbook.getSheet("成绩汇总");
            assertEquals("最终成绩（80%）", sheet.getRow(0).getCell(6).getStringCellValue());
            assertEquals(17, sheet.getRow(1).getCell(5).getNumericCellValue());
            assertEquals(13.6, sheet.getRow(1).getCell(6).getNumericCellValue(), 0.0001);
        }
    }

    @Test
    void saveFinalScoreReadsSubmissionAcrossTenantsAndKeepsReviewTenant() {
        TenantUtils.clear();
        TenantUtils.setCurrentTenant(99L, "reviewer");

        ExamSubmission submission = submission(21L, 11L, 101L);
        submission.setTenantId(4L);
        ExamEnrollment enrollment = new ExamEnrollment();
        enrollment.setId(1L);
        enrollment.setCampaignId(2L);
        ExamCampaign campaign = new ExamCampaign();
        campaign.setId(2L);

        when(submissionMapper.selectById(21L)).thenAnswer(invocation -> {
            assertTrue(TenantUtils.isIgnoreTenant());
            return submission;
        });
        when(enrollmentMapper.selectById(1L)).thenReturn(enrollment);
        when(campaignMapper.selectById(2L)).thenReturn(campaign);
        when(questionMapper.selectById(11L)).thenReturn(question(11L, 25, 1));
        when(reviewMapper.selectOne(any())).thenReturn(null);

        try {
            service.saveFinalScore(21L, 24, "", 1001L);

            assertFalse(TenantUtils.isIgnoreTenant());
            ArgumentCaptor<AssessmentReview> reviewCaptor = ArgumentCaptor.forClass(AssessmentReview.class);
            verify(reviewMapper).insert(reviewCaptor.capture());
            assertEquals(4L, reviewCaptor.getValue().getTenantId());
            assertEquals(24, reviewCaptor.getValue().getTotalScore());
            assertEquals("", reviewCaptor.getValue().getComment());
            assertEquals("{\"scoreMode\":\"QUESTION_SCORE\"}", reviewCaptor.getValue().getChecklistJson());
            assertThrows(IllegalArgumentException.class,
                    () -> service.saveFinalScore(21L, 26, "超过题目满分", 1001L));
        } finally {
            TenantUtils.clear();
        }
    }

    private ExamQuestion question(Long id, int score, int sortOrder) {
        ExamQuestion question = new ExamQuestion();
        question.setId(id);
        question.setCampaignId(2L);
        question.setScore(score);
        question.setSortOrder(sortOrder);
        question.setTitle("测试题目");
        return question;
    }

    private ExamSubmission submission(Long id, Long questionId, Long agentId) {
        ExamSubmission submission = new ExamSubmission();
        submission.setId(id);
        submission.setEnrollmentId(1L);
        submission.setQuestionId(questionId);
        submission.setAgentId(agentId);
        submission.setWorkType("AGENT");
        submission.setStatus("REVIEW_PENDING");
        submission.setFormJson("{}");
        return submission;
    }

    private AssessmentReport report(Long submissionId, int totalScore) {
        AssessmentReport report = new AssessmentReport();
        report.setSubmissionId(submissionId);
        report.setTotalScore(totalScore);
        return report;
    }

    private AssessmentReview directReview(Long submissionId, int totalScore) {
        AssessmentReview review = new AssessmentReview();
        review.setSubmissionId(submissionId);
        review.setTotalScore(totalScore);
        review.setComment("复核通过");
        review.setStatus("PUBLISHED");
        review.setChecklistJson("{\"scoreMode\":\"QUESTION_SCORE\"}");
        return review;
    }
}
