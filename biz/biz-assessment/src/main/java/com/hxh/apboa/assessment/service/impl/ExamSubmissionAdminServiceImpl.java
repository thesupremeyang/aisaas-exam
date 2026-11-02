package com.hxh.apboa.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hxh.apboa.assessment.dto.ExamSubmissionAdminVO;
import com.hxh.apboa.assessment.mapper.ExamEnrollmentMapper;
import com.hxh.apboa.assessment.mapper.ExamQuestionMapper;
import com.hxh.apboa.assessment.mapper.ExamSubmissionMapper;
import com.hxh.apboa.assessment.service.ExamSubmissionAdminService;
import com.hxh.apboa.account.mapper.AccountMapper;
import com.hxh.apboa.agent.service.AgentDefinitionService;
import com.hxh.apboa.common.entity.Account;
import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.entity.ExamEnrollment;
import com.hxh.apboa.common.entity.ExamQuestion;
import com.hxh.apboa.common.entity.ExamSubmission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 平台 admin 专用答卷管理查询实现。
 *
 * @author workbuddy
 */
@Service
@RequiredArgsConstructor
public class ExamSubmissionAdminServiceImpl implements ExamSubmissionAdminService {

    private final ExamEnrollmentMapper enrollmentMapper;
    private final ExamSubmissionMapper submissionMapper;
    private final ExamQuestionMapper questionMapper;
    private final AccountMapper accountMapper;
    private final AgentDefinitionService agentDefinitionService;

    @Override
    public List<ExamSubmissionAdminVO> listCampaignSubmissions(Long campaignId) {
        List<ExamEnrollment> enrollments = enrollmentMapper.selectList(
                new LambdaQueryWrapper<ExamEnrollment>()
                        .eq(ExamEnrollment::getCampaignId, campaignId));
        if (enrollments.isEmpty()) {
            return List.of();
        }
        Map<Long, ExamEnrollment> enrollmentById = enrollments.stream()
                .collect(Collectors.toMap(ExamEnrollment::getId, Function.identity(), (a, b) -> a));

        List<Long> enrollmentIds = new ArrayList<>(enrollmentById.keySet());
        // 排除 form_json 大字段，列表页不需要答卷内容
        List<ExamSubmission> submissions = submissionMapper.selectList(
                new LambdaQueryWrapper<ExamSubmission>()
                        .select(ExamSubmission.class, field -> !"form_json".equals(field.getColumn()))
                        .in(ExamSubmission::getEnrollmentId, enrollmentIds)
                        .orderByDesc(ExamSubmission::getSubmittedAt));

        Map<Long, ExamQuestion> questionById = questionMapper.selectList(
                        new LambdaQueryWrapper<ExamQuestion>()
                                .eq(ExamQuestion::getCampaignId, campaignId)).stream()
                .collect(Collectors.toMap(ExamQuestion::getId, Function.identity(), (a, b) -> a));

        Set<Long> agentIds = submissions.stream()
                .map(ExamSubmission::getAgentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> agentNameById = agentIds.isEmpty()
                ? Collections.emptyMap()
                : agentDefinitionService.listByIds(agentIds).stream()
                        .collect(Collectors.toMap(
                                AgentDefinition::getId,
                                agent -> agent.getName() == null ? "" : agent.getName(),
                                (a, b) -> a));

        // 考生姓名取账号昵称（真名），考号取登录账号；报名表 name 存的是批次名，不可用作姓名
        Set<Long> accountIds = enrollments.stream()
                .map(ExamEnrollment::getAccountId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Account> accountById = accountIds.isEmpty()
                ? Collections.emptyMap()
                : accountMapper.selectBatchIds(accountIds).stream()
                        .collect(Collectors.toMap(Account::getId, Function.identity(), (a, b) -> a));

        List<ExamSubmissionAdminVO> rows = new ArrayList<>(submissions.size());
        for (ExamSubmission submission : submissions) {
            ExamSubmissionAdminVO row = new ExamSubmissionAdminVO();
            row.setSubmissionId(submission.getId());
            row.setEnrollmentId(submission.getEnrollmentId());
            ExamEnrollment enrollment = enrollmentById.get(submission.getEnrollmentId());
            if (enrollment != null) {
                row.setTenantId(enrollment.getTenantId());
                Account account = enrollment.getAccountId() == null
                        ? null
                        : accountById.get(enrollment.getAccountId());
                row.setCandidateName(account != null && account.getNickname() != null
                        ? account.getNickname()
                        : enrollment.getName());
                row.setCandidateUsername(account != null ? account.getUsername() : null);
                row.setOrganization(enrollment.getOrganization());
            }
            ExamQuestion question = submission.getQuestionId() == null
                    ? null
                    : questionById.get(submission.getQuestionId());
            if (question != null) {
                row.setQuestionId(question.getId());
                row.setQuestionTitle(question.getTitle());
                row.setQuestionScore(question.getScore());
            }
            row.setAgentId(submission.getAgentId());
            row.setAgentName(submission.getAgentId() == null
                    ? null
                    : agentNameById.get(submission.getAgentId()));
            row.setWorkType(submission.getWorkType());
            row.setStatus(submission.getStatus());
            row.setCurrentStep(submission.getCurrentStep());
            row.setSubmittedAt(submission.getSubmittedAt());
            rows.add(row);
        }
        return rows;
    }
}
