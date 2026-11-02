package com.hxh.apboa.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hxh.apboa.assessment.mapper.ExamCampaignMapper;
import com.hxh.apboa.assessment.mapper.ExamEnrollmentMapper;
import com.hxh.apboa.assessment.mapper.ExamQuestionMapper;
import com.hxh.apboa.assessment.mapper.ExamSubmissionMapper;
import com.hxh.apboa.assessment.mapper.AssessmentResultMapper;
import com.hxh.apboa.assessment.mapper.AssessmentReviewMapper;
import com.hxh.apboa.assessment.service.AssessmentRubric;
import com.hxh.apboa.assessment.service.CandidateExamService;
import com.hxh.apboa.assessment.service.ExamCampaignTiming;
import com.hxh.apboa.assessment.service.ExamCampaignScoreMode;
import com.hxh.apboa.assessment.service.ExamSsoService;
import com.hxh.apboa.agent.mapper.AgentDefinitionMapper;
import com.hxh.apboa.agent.service.AgentDefinitionService;
import com.hxh.apboa.common.exception.BusinessException;
import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.entity.AssessmentResult;
import com.hxh.apboa.common.entity.AssessmentReview;
import com.hxh.apboa.common.entity.ExamCampaign;
import com.hxh.apboa.common.entity.ExamEnrollment;
import com.hxh.apboa.common.entity.ExamQuestion;
import com.hxh.apboa.common.entity.ExamSubmission;
import com.hxh.apboa.common.vo.ExamAgentSummaryVO;
import com.hxh.apboa.common.vo.ExamCandidateScoreVO;
import com.hxh.apboa.common.vo.AgentDefinitionVO;
import com.hxh.apboa.common.vo.ExamSsoTicketIssueVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 学员端考试门户业务实现。
 */
@Service
@RequiredArgsConstructor
public class CandidateExamServiceImpl implements CandidateExamService {

    private final ExamCampaignMapper campaignMapper;
    private final ExamEnrollmentMapper enrollmentMapper;
    private final ExamQuestionMapper questionMapper;
    private final ExamSubmissionMapper submissionMapper;
    private final AssessmentResultMapper resultMapper;
    private final AssessmentReviewMapper reviewMapper;
    private final AgentDefinitionMapper agentDefinitionMapper;
    private final AgentDefinitionService agentDefinitionService;
    private final ExamSsoService ssoService;

    /** 对考生开放访问的批次状态集合。 */
    private static final List<String> ACTIVE_STATUSES = List.of("OPEN", "REGISTERING", "IN_PROGRESS", "PUBLISH");

    @Override
    public List<ExamCampaign> listActiveCampaigns(Long accountId) {
        if (accountId == null) {
            return List.of();
        }
        var enrolledCampaignIds = enrollmentMapper.selectList(new LambdaQueryWrapper<ExamEnrollment>()
                        .select(ExamEnrollment::getCampaignId)
                        .eq(ExamEnrollment::getAccountId, accountId)
                        .eq(ExamEnrollment::getEnabled, true)
                        .eq(ExamEnrollment::getStatus, "REGISTERED")
                        .apply("del_flag = 0"))
                .stream()
                .map(ExamEnrollment::getCampaignId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (enrolledCampaignIds.isEmpty()) {
            return List.of();
        }
        var now = LocalDateTime.now();
        var list = campaignMapper.selectList(new LambdaQueryWrapper<ExamCampaign>()
                .in(ExamCampaign::getId, enrolledCampaignIds)
                .in(ExamCampaign::getStatus, ACTIVE_STATUSES)
                .eq(ExamCampaign::getEnabled, true)
                .orderByDesc(ExamCampaign::getCreatedAt));
        // 服务端二次过滤：普通批次受评审截止时间限制，已开启查分模式的批次可继续展示
        return list.stream()
                .filter(c -> ExamCampaignScoreMode.isEnabled(c)
                        || c.getReviewEnd() == null || !now.isAfter(c.getReviewEnd()))
                .peek(ExamCampaignScoreMode::decorate)
                .toList();
    }

    @Override
    public void requireEnrollment(Long campaignId, Long accountId) {
        if (findEnrollment(campaignId, accountId) == null) {
            throw new BusinessException("当前账号未加入本场考试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CandidateProgress getProgress(Long campaignId, Long accountId) {
        var campaign = campaignMapper.selectById(campaignId);
        var now = LocalDateTime.now();
        boolean registrationOpen = ExamCampaignTiming.isRegistrationOpen(campaign, now);
        boolean submissionOpen = ExamCampaignTiming.isSubmissionOpen(campaign, now);
        String examState = ExamCampaignTiming.state(campaign, now).name();

        int maxWorks = AssessmentRubric.fromCampaignConfig(campaign == null ? null : campaign.getConfigJson()).maxWorks();
        boolean scoreQueryEnabled = ExamCampaignScoreMode.isEnabled(campaign);
        if (campaign == null
                || !Boolean.TRUE.equals(campaign.getEnabled())
                || (!ACTIVE_STATUSES.contains(campaign.getStatus()) && !scoreQueryEnabled)) {
            return new CandidateProgress(false, null, null, null, false, registrationOpen, submissionOpen,
                    scoreQueryEnabled,
                    maxWorks, examState, null, null, now);
        }

        // 报名关系由管理员预先分配；未报名账号不得通过传入批次 ID 进入考试。
        var enrollment = findEnrollment(campaignId, accountId);
        if (enrollment == null) {
            return new CandidateProgress(false, null, null, null, false, registrationOpen, submissionOpen,
                    scoreQueryEnabled,
                    maxWorks, examState, campaign.getSubmissionStart(), campaign.getSubmissionEnd(), now);
        }

        var submission = loadLatestSubmission(enrollment);
        markEnteredIfOpen(enrollment, submissionOpen, now);
        boolean published = submission != null && "PUBLISHED".equals(submission.getStatus());
        return new CandidateProgress(true, enrollment.getId(),
                submission == null ? null : submission.getId(),
                submission == null ? null : submission.getStatus(),
                published, registrationOpen, submissionOpen, scoreQueryEnabled, maxWorks, examState,
                campaign == null ? null : campaign.getSubmissionStart(),
                campaign == null ? null : campaign.getSubmissionEnd(), now);
    }

    @Override
    public ExamCandidateScoreVO getScore(Long campaignId, Long accountId) {
        ExamCampaign campaign = campaignMapper.selectById(campaignId);
        if (campaign == null || !Boolean.TRUE.equals(campaign.getEnabled())
                || !ExamCampaignScoreMode.isEnabled(campaign)
                || ExamCampaignTiming.state(campaign, LocalDateTime.now()) != ExamCampaignTiming.State.ENDED) {
            throw new BusinessException("当前考试尚未开启查分模式");
        }
        ExamEnrollment enrollment = findEnrollment(campaignId, accountId);
        if (enrollment == null) {
            throw new BusinessException("当前账号未加入本场考试");
        }

        List<ExamQuestion> questions = questionMapper.selectList(new LambdaQueryWrapper<ExamQuestion>()
                .eq(ExamQuestion::getCampaignId, campaignId)
                .eq(ExamQuestion::getEnabled, true)
                .orderByAsc(ExamQuestion::getSortOrder)
                .orderByAsc(ExamQuestion::getId));
        List<ExamSubmission> submissions = submissionMapper.selectList(new LambdaQueryWrapper<ExamSubmission>()
                .eq(ExamSubmission::getEnrollmentId, enrollment.getId())
                .ne(ExamSubmission::getStatus, "DRAFT")
                .apply("del_flag = 0")
                .orderByDesc(ExamSubmission::getSubmittedAt)
                .orderByDesc(ExamSubmission::getUpdatedAt));
        Map<Long, ExamSubmission> submissionByQuestion = submissions.stream()
                .filter(s -> s.getQuestionId() != null)
                .collect(Collectors.toMap(ExamSubmission::getQuestionId, s -> s,
                        (first, ignored) -> first, HashMap::new));

        List<Long> submissionIds = submissions.stream().map(ExamSubmission::getId)
                .filter(Objects::nonNull).toList();
        Map<Long, AssessmentResult> resultBySubmission = submissionIds.isEmpty() ? Map.of()
                : resultMapper.selectList(new LambdaQueryWrapper<AssessmentResult>()
                        .in(AssessmentResult::getSubmissionId, submissionIds)
                        .apply("del_flag = 0"))
                .stream().collect(Collectors.toMap(AssessmentResult::getSubmissionId, r -> r,
                        (first, ignored) -> first));
        Map<Long, AssessmentReview> reviewBySubmission = submissionIds.isEmpty() ? Map.of()
                : reviewMapper.selectList(new LambdaQueryWrapper<AssessmentReview>()
                        .in(AssessmentReview::getSubmissionId, submissionIds))
                .stream().collect(Collectors.toMap(AssessmentReview::getSubmissionId, r -> r,
                        (first, ignored) -> first, HashMap::new));

        // 实操分优先取发布时固化的值；未发布但已有复核结果时按已复核题目求和，
        // 不再要求「每一份提交都有成绩」——那样只要有一题没复核，考生就一分都看不到。
        Integer practicalScore = enrollment.getPracticalScore();
        boolean practicalPublished = practicalScore != null;
        if (practicalScore == null && !resultBySubmission.isEmpty()) {
            practicalScore = resultBySubmission.values().stream()
                    .map(AssessmentResult::getFinalScore)
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .sum();
        }
        ExamCandidateScoreVO score = new ExamCandidateScoreVO();
        score.setCampaignId(campaign.getId());
        score.setCampaignName(campaign.getName());
        score.setTheoryScore(enrollment.getTheoryScore());
        score.setPracticalScore(practicalScore);
        score.setTheoryScoreEntered(enrollment.getTheoryScore() != null);
        score.setPracticalScorePublished(practicalPublished);
        if (score.getTheoryScore() != null && practicalScore != null) {
            score.setTotalScore(BigDecimal.valueOf(score.getTheoryScore())
                    .multiply(BigDecimal.valueOf(0.2d))
                    .add(BigDecimal.valueOf(practicalScore).multiply(BigDecimal.valueOf(0.8d)))
                    .setScale(1, RoundingMode.HALF_UP));
        }

        Map<Long, AgentDefinition> agentMap = new HashMap<>();
        submissionByQuestion.values().stream()
                .map(ExamSubmission::getAgentId)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(id -> {
                    AgentDefinition agent = agentDefinitionMapper.selectById(id);
                    if (agent != null) agentMap.put(id, agent);
                });
        List<ExamCandidateScoreVO.QuestionScore> questionScores = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            ExamQuestion question = questions.get(i);
            ExamSubmission submission = submissionByQuestion.get(question.getId());
            AgentDefinition agent = submission == null || submission.getAgentId() == null
                    ? null : agentMap.get(submission.getAgentId());
            ExamCandidateScoreVO.QuestionScore item = new ExamCandidateScoreVO.QuestionScore();
            item.setQuestionNo(i + 1);
            item.setQuestionId(question.getId());
            item.setQuestionTitle(question.getTitle());
            item.setQuestionScore(question.getScore());
            if (submission != null) {
                item.setSubmissionId(submission.getId());
                item.setAgentId(submission.getAgentId());
                item.setAgentName(agent == null ? null : agent.getName());
                AssessmentResult result = resultBySubmission.get(submission.getId());
                if (result != null) {
                    item.setFinalScore(result.getFinalScore());
                }
                AssessmentReview review = reviewBySubmission.get(submission.getId());
                if (review != null) {
                    item.setReviewComment(review.getComment());
                }
            }
            questionScores.add(item);
        }
        score.setQuestions(questionScores);
        return score;
    }

    @Override
    public AgentDefinitionVO getSubmittedAgentConfiguration(Long submissionId, Long accountId) {
        ExamSubmission submission = submissionMapper.selectById(submissionId);
        ExamEnrollment enrollment = submission == null ? null : enrollmentMapper.selectById(submission.getEnrollmentId());
        ExamCampaign campaign = enrollment == null ? null : campaignMapper.selectById(enrollment.getCampaignId());
        if (submission == null || enrollment == null || !accountId.equals(enrollment.getAccountId())
                || campaign == null || !Boolean.TRUE.equals(campaign.getEnabled())
                || !ExamCampaignScoreMode.isEnabled(campaign)
                || ExamCampaignTiming.state(campaign, LocalDateTime.now()) != ExamCampaignTiming.State.ENDED) {
            throw new BusinessException("当前作品暂不可查看智能体配置");
        }
        if (submission.getAgentId() == null) {
            throw new BusinessException("该作品未关联智能体");
        }
        AgentDefinition agent = agentDefinitionMapper.selectOne(new LambdaQueryWrapper<AgentDefinition>()
                .eq(AgentDefinition::getId, submission.getAgentId())
                .eq(AgentDefinition::getTenantId, enrollment.getTenantId())
                .apply("del_flag = 0"));
        if (agent == null) {
            throw new BusinessException("关联的智能体不存在");
        }
        return agentDefinitionService.agentDefinitionDetail(agent.getId());
    }

    private ExamSubmission loadLatestSubmission(ExamEnrollment enrollment) {
        List<ExamSubmission> submissions = submissionMapper.selectList(new LambdaQueryWrapper<ExamSubmission>()
                .eq(ExamSubmission::getEnrollmentId, enrollment.getId())
                .apply("del_flag = 0")
                .orderByDesc(ExamSubmission::getCreatedAt)
                .last("LIMIT 1"));
        return submissions.isEmpty() ? null : submissions.get(0);
    }

    private ExamEnrollment findEnrollment(Long campaignId, Long accountId) {
        if (campaignId == null || accountId == null) {
            return null;
        }
        return enrollmentMapper.selectOne(new LambdaQueryWrapper<ExamEnrollment>()
                .eq(ExamEnrollment::getCampaignId, campaignId)
                .eq(ExamEnrollment::getAccountId, accountId)
                .eq(ExamEnrollment::getEnabled, true)
                .eq(ExamEnrollment::getStatus, "REGISTERED")
                .apply("del_flag = 0")
                .last("LIMIT 1"));
    }

    @Override
    public List<ExamSubmission> listSubmissions(Long enrollmentId, Long accountId) {
        var enrollment = enrollmentMapper.selectById(enrollmentId);
        if (enrollment == null || !accountId.equals(enrollment.getAccountId())) {
            throw new IllegalStateException("无权限访问该报名记录");
        }
        return submissionMapper.selectList(new LambdaQueryWrapper<ExamSubmission>()
                .eq(ExamSubmission::getEnrollmentId, enrollmentId)
                .apply("del_flag = 0")
                .orderByAsc(ExamSubmission::getCreatedAt));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamSubmission createSubmission(Long enrollmentId, Long accountId, Long questionId) {
        var enrollment = enrollmentMapper.selectById(enrollmentId);
        if (enrollment == null || !accountId.equals(enrollment.getAccountId())) {
            throw new IllegalStateException("无权限访问该报名记录");
        }
        if (questionId == null) {
            throw new IllegalStateException("请指定要提交的试题");
        }
        // 检查该考生是否已为该试题创建过提交
        long existingForQuestion = submissionMapper.selectCount(new LambdaQueryWrapper<ExamSubmission>()
                .eq(ExamSubmission::getEnrollmentId, enrollmentId)
                .eq(ExamSubmission::getQuestionId, questionId)
                .apply("del_flag = 0"));
        if (existingForQuestion > 0) {
            throw new IllegalStateException("该试题已有提交记录，请勿重复创建");
        }
        int maxWorks = resolveMaxWorks(enrollment.getCampaignId());
        long existingCount = submissionMapper.selectCount(new LambdaQueryWrapper<ExamSubmission>()
                .eq(ExamSubmission::getEnrollmentId, enrollmentId)
                .apply("del_flag = 0"));
        if (existingCount >= maxWorks) {
            throw new IllegalStateException("已达到本批次可提交作品数量上限（最多 " + maxWorks + " 个）");
        }
        int baseMax = submissionMapper.selectList(new LambdaQueryWrapper<ExamSubmission>()
                .eq(ExamSubmission::getEnrollmentId, enrollmentId)
                .apply("del_flag = 0")
                .select(ExamSubmission::getVersion))
                .stream().mapToInt(s -> s.getVersion() == null ? 0 : s.getVersion()).max().orElse(0);
        String prefillFormJson = resolvePrefillFormJson(enrollmentId);
        var submission = new ExamSubmission();
        submission.setEnrollmentId(enrollment.getId());
        submission.setQuestionId(questionId);
        submission.setWorkType("AGENT");
        submission.setCurrentStep(1);
        submission.setStatus("DRAFT");
        submission.setFormJson(prefillFormJson);
        submission.setVersion(baseMax + 1);
        try {
            submissionMapper.insert(submission);
            return submission;
        } catch (DuplicateKeyException duplicated) {
            // 并发兜底：上面的 selectCount 是 check-then-act，双击/重试/多标签页时
            // 两个请求可能同时通过检查。数据库唯一键（active_pair）会拦下第二个，
            // 这里直接返回已存在的那条，保持接口幂等，而不是抛错给考生。
            var existing = submissionMapper.selectOne(new LambdaQueryWrapper<ExamSubmission>()
                    .eq(ExamSubmission::getEnrollmentId, enrollmentId)
                    .eq(ExamSubmission::getQuestionId, questionId)
                    .apply("del_flag = 0")
                    .orderByAsc(ExamSubmission::getId)
                    .last("limit 1"));
            if (existing != null) {
                return existing;
            }
            throw new IllegalStateException("该试题已有提交记录，请勿重复创建");
        }
    }

    @Override
    public List<ExamAgentSummaryVO> listMyAgents(Long campaignId, Long accountId, Long tenantId) {
        return ssoService.listCandidateAgents(campaignId, accountId, tenantId);
    }

    @Override
    public ExamSsoTicketIssueVO issueSsoTicket(Long campaignId, String returnUrl, Long accountId, Long tenantId) {
        return ssoService.issueTicket(campaignId, returnUrl, accountId, tenantId);
    }

    private void markEnteredIfOpen(ExamEnrollment enrollment, boolean submissionOpen, LocalDateTime now) {
        if (!submissionOpen || enrollment.getEnteredAt() != null) {
            return;
        }
        int updated = enrollmentMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ExamEnrollment>()
                .eq(ExamEnrollment::getId, enrollment.getId())
                .isNull(ExamEnrollment::getEnteredAt)
                .set(ExamEnrollment::getEnteredAt, now));
        if (updated == 1) {
            enrollment.setEnteredAt(now);
        }
    }

    /**
     * 取该报名最近一次提交（不含本次新建）的身份核验信息，构造成仅含 applicant 的 formJson。
     * 若从未提交过则返回 null，由前端回退到空白表单。
     */
    private String resolvePrefillFormJson(Long enrollmentId) {
        ExamSubmission last = submissionMapper.selectOne(new LambdaQueryWrapper<ExamSubmission>()
                .eq(ExamSubmission::getEnrollmentId, enrollmentId)
                .apply("del_flag = 0")
                .ne(ExamSubmission::getStatus, "DRAFT")
                .orderByDesc(ExamSubmission::getCreatedAt)
                .last("LIMIT 1"));
        if (last == null) {
            // 兜底：该报名下没有已提交的记录时，取最近一条填写过表单的草稿
            last = submissionMapper.selectOne(new LambdaQueryWrapper<ExamSubmission>()
                    .eq(ExamSubmission::getEnrollmentId, enrollmentId)
                    .apply("del_flag = 0")
                    .isNotNull(ExamSubmission::getFormJson)
                    .orderByDesc(ExamSubmission::getCreatedAt)
                    .last("LIMIT 1"));
        }
        if (last == null || last.getFormJson() == null) {
            return null;
        }
        try {
            JsonNode root = new ObjectMapper().readTree(last.getFormJson());
            JsonNode applicant = root.get("applicant");
            if (applicant == null || !applicant.isObject()) {
                return null;
            }
            com.fasterxml.jackson.databind.node.ObjectNode node = new ObjectMapper().createObjectNode();
            node.set("applicant", applicant);
            return node.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public void deleteSubmission(Long submissionId, Long accountId) {
        ExamSubmission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            return;
        }
        var enrollment = enrollmentMapper.selectById(submission.getEnrollmentId());
        if (enrollment == null || !accountId.equals(enrollment.getAccountId())) {
            throw new IllegalStateException("无权限操作该作品");
        }
        if (!"DRAFT".equals(submission.getStatus())) {
            throw new IllegalStateException("仅草稿作品可以删除");
        }
        submissionMapper.update(null, new UpdateWrapper<ExamSubmission>()
                .eq("id", submissionId)
                .set("del_flag", 1));
    }

    private int resolveMaxWorks(Long campaignId) {
        if (campaignId == null) {
            return 3;
        }
        var campaign = campaignMapper.selectById(campaignId);
        return AssessmentRubric.fromCampaignConfig(campaign == null ? null : campaign.getConfigJson()).maxWorks();
    }

}
