package com.hxh.apboa.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hxh.apboa.account.mapper.AccountMapper;
import com.hxh.apboa.assessment.mapper.ExamCampaignMapper;
import com.hxh.apboa.assessment.mapper.ExamEnrollmentMapper;
import com.hxh.apboa.assessment.mapper.ExamSubmissionMapper;
import com.hxh.apboa.assessment.service.ExamCandidateStatusService;
import com.hxh.apboa.assessment.service.ExamCampaignTiming;
import com.hxh.apboa.common.UserDetail;
import com.hxh.apboa.common.entity.Account;
import com.hxh.apboa.common.entity.ExamCampaign;
import com.hxh.apboa.common.entity.ExamEnrollment;
import com.hxh.apboa.common.entity.ExamSubmission;
import com.hxh.apboa.common.exception.BusinessException;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.common.util.RedisKeyBuilder;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.common.vo.ExamCandidateStatusSnapshotVO;
import com.hxh.apboa.common.vo.ExamCandidateStatusVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 管理端考生状态聚合实现。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamCandidateStatusServiceImpl implements ExamCandidateStatusService {

    private final ExamCampaignMapper campaignMapper;
    private final ExamEnrollmentMapper enrollmentMapper;
    private final ExamSubmissionMapper submissionMapper;
    private final AccountMapper accountMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public ExamCandidateStatusSnapshotVO getSnapshot(Long campaignId) {
        if (campaignId == null) {
            throw new BusinessException("考试批次不能为空");
        }

        TenantUtils.setIgnoreTenant();
        try {
            ExamCampaign campaign = campaignMapper.selectById(campaignId);
            if (campaign == null) {
                throw new BusinessException("考试批次不存在");
            }

            LocalDateTime now = LocalDateTime.now();
            ExamCampaignTiming.State state = ExamCampaignTiming.state(campaign, now);
            List<ExamEnrollment> enrollments = enrollmentMapper.selectList(new LambdaQueryWrapper<ExamEnrollment>()
                    .eq(ExamEnrollment::getCampaignId, campaignId)
                    .eq(ExamEnrollment::getEnabled, true)
                    .eq(ExamEnrollment::getStatus, "REGISTERED")
                    .apply("del_flag = 0")
                    .orderByAsc(ExamEnrollment::getCreatedAt));

            Map<Long, Account> accountMap = loadAccounts(enrollments);
            Map<Long, ExamSubmission> submissionMap = loadSubmissions(enrollments);
            LoginStatus loginStatus = loadLoginStatus();

            List<ExamCandidateStatusVO> candidates = enrollments.stream()
                    .map(enrollment -> toStatus(enrollment, accountMap, submissionMap, loginStatus.accountIds(), campaign, state, now))
                    .toList();

            ExamCandidateStatusSnapshotVO snapshot = new ExamCandidateStatusSnapshotVO();
            snapshot.setCampaignId(campaign.getId());
            snapshot.setCampaignName(campaign.getName());
            snapshot.setExamState(state.name());
            snapshot.setExamStartAt(campaign.getSubmissionStart());
            snapshot.setExamEndAt(campaign.getSubmissionEnd());
            snapshot.setServerNow(now);
            snapshot.setLoginStatusAvailable(loginStatus.available());
            snapshot.setTotalCandidates(candidates.size());
            snapshot.setLoggedInCount((int) candidates.stream().filter(ExamCandidateStatusVO::isLoggedIn).count());
            snapshot.setEnteredCount((int) candidates.stream().filter(ExamCandidateStatusVO::isEntered).count());
            snapshot.setSubmittedCount((int) candidates.stream()
                    .filter(candidate -> candidate.getSubmissionStatus() != null
                            && !"DRAFT".equals(candidate.getSubmissionStatus()))
                    .count());
            snapshot.setCandidates(candidates);
            return snapshot;
        } finally {
            TenantUtils.clearIgnoreTenant();
        }
    }

    private Map<Long, Account> loadAccounts(List<ExamEnrollment> enrollments) {
        List<Long> accountIds = enrollments.stream().map(ExamEnrollment::getAccountId)
                .filter(id -> id != null).distinct().toList();
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        return accountMapper.selectList(new LambdaQueryWrapper<Account>().in(Account::getId, accountIds))
                .stream().collect(Collectors.toMap(Account::getId, Function.identity()));
    }

    private Map<Long, ExamSubmission> loadSubmissions(List<ExamEnrollment> enrollments) {
        List<Long> enrollmentIds = enrollments.stream().map(ExamEnrollment::getId)
                .filter(id -> id != null).toList();
        if (enrollmentIds.isEmpty()) {
            return Map.of();
        }
        return submissionMapper.selectList(new LambdaQueryWrapper<ExamSubmission>()
                        .in(ExamSubmission::getEnrollmentId, enrollmentIds))
                .stream().collect(Collectors.toMap(ExamSubmission::getEnrollmentId, Function.identity(), (left, right) -> left));
    }

    private LoginStatus loadLoginStatus() {
        try {
            // access token 的有效期较长，不能代表用户仍打开页面；只统计短 TTL 在线标记。
            Set<String> keys = stringRedisTemplate.keys(RedisKeyBuilder.globalKey("presence:*") );
            if (keys == null || keys.isEmpty()) {
                return new LoginStatus(Collections.emptySet(), true);
            }
            List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);
            if (values == null) {
                return new LoginStatus(Collections.emptySet(), true);
            }
            Set<Long> accountIds = values.stream().map(this::parseUserDetail)
                    .filter(detail -> detail != null && detail.getId() != null)
                    .map(UserDetail::getId).collect(Collectors.toSet());
            return new LoginStatus(accountIds, true);
        } catch (Exception exception) {
            log.warn("读取考生登录状态失败", exception);
            return new LoginStatus(Collections.emptySet(), false);
        }
    }

    private UserDetail parseUserDetail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return JsonUtils.parse(value, UserDetail.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private ExamCandidateStatusVO toStatus(ExamEnrollment enrollment,
                                           Map<Long, Account> accountMap,
                                           Map<Long, ExamSubmission> submissionMap,
                                           Set<Long> loggedInAccountIds,
                                           ExamCampaign campaign,
                                           ExamCampaignTiming.State state,
                                           LocalDateTime now) {
        Account account = accountMap.get(enrollment.getAccountId());
        ExamSubmission submission = submissionMap.get(enrollment.getId());

        ExamCandidateStatusVO candidate = new ExamCandidateStatusVO();
        candidate.setEnrollmentId(enrollment.getId());
        candidate.setAccountId(enrollment.getAccountId());
        candidate.setCandidateName(account == null ? enrollment.getName() : firstNonBlank(account.getNickname(), account.getUsername(), enrollment.getName()));
        candidate.setUsername(account == null ? null : account.getUsername());
        candidate.setEmail(account == null ? null : account.getEmail());
        candidate.setAccountEnabled(account != null && Boolean.TRUE.equals(account.getEnabled()));
        candidate.setOrganization(enrollment.getOrganization());
        candidate.setLoggedIn(loggedInAccountIds.contains(enrollment.getAccountId()));
        candidate.setEntered(enrollment.getEnteredAt() != null);
        candidate.setEnteredAt(enrollment.getEnteredAt());
        candidate.setSubmissionStatus(submission == null ? null : submission.getStatus());
        candidate.setExamState(state.name());
        candidate.setElapsedSeconds(elapsedSeconds(campaign, state, now));
        candidate.setRemainingSeconds(remainingSeconds(campaign, state, now));
        return candidate;
    }

    private String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        return fallback;
    }

    private long elapsedSeconds(ExamCampaign campaign, ExamCampaignTiming.State state, LocalDateTime now) {
        if (campaign.getSubmissionStart() == null || state == ExamCampaignTiming.State.WAITING) {
            return 0;
        }
        LocalDateTime end = state == ExamCampaignTiming.State.ENDED && campaign.getSubmissionEnd() != null
                ? campaign.getSubmissionEnd() : now;
        return Math.max(0, Duration.between(campaign.getSubmissionStart(), end).getSeconds());
    }

    private long remainingSeconds(ExamCampaign campaign, ExamCampaignTiming.State state, LocalDateTime now) {
        LocalDateTime target = state == ExamCampaignTiming.State.WAITING
                ? campaign.getSubmissionStart() : campaign.getSubmissionEnd();
        if (target == null || state == ExamCampaignTiming.State.ENDED) {
            return 0;
        }
        return Math.max(0, Duration.between(now, target).getSeconds());
    }

    private record LoginStatus(Set<Long> accountIds, boolean available) {
    }
}
