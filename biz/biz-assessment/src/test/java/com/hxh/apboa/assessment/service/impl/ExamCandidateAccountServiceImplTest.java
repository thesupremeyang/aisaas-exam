package com.hxh.apboa.assessment.service.impl;

import com.hxh.apboa.account.mapper.AccountMapper;
import com.hxh.apboa.account.service.AccountService;
import com.hxh.apboa.assessment.mapper.ExamCampaignMapper;
import com.hxh.apboa.assessment.mapper.ExamEnrollmentMapper;
import com.hxh.apboa.common.dto.ExamCandidateAccountCreateRequest;
import com.hxh.apboa.common.dto.ExamCandidateAccountUpdateRequest;
import com.hxh.apboa.common.dto.RegisterRequest;
import com.hxh.apboa.common.entity.Account;
import com.hxh.apboa.common.entity.ExamCampaign;
import com.hxh.apboa.common.entity.ExamEnrollment;
import com.hxh.apboa.common.exception.BusinessException;
import com.hxh.apboa.common.util.CryptoUtils;
import com.hxh.apboa.common.util.TenantUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamCandidateAccountServiceImplTest {
    @Mock
    private ExamCampaignMapper campaignMapper;
    @Mock
    private ExamEnrollmentMapper enrollmentMapper;
    @Mock
    private AccountMapper accountMapper;
    @Mock
    private AccountService accountService;

    private ExamCandidateAccountServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ExamCandidateAccountServiceImpl(
                campaignMapper, enrollmentMapper, accountMapper, accountService);
    }

    @AfterEach
    void clearTenantContext() {
        TenantUtils.clear();
    }

    @Test
    void createsIsolatedAccountAndEnrollmentForCampaign() {
        ExamCampaign campaign = new ExamCampaign();
        campaign.setId(10L);
        when(campaignMapper.selectById(10L)).thenReturn(campaign);
        when(accountService.register(any(RegisterRequest.class))).thenReturn(true);

        Account account = new Account();
        account.setId(20L);
        account.setLastTenantId(30L);
        when(accountMapper.selectOne(any())).thenReturn(account);
        when(enrollmentMapper.insert(any(ExamEnrollment.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, ExamEnrollment.class).setId(40L);
            return 1;
        });

        ExamCandidateAccountCreateRequest request = new ExamCandidateAccountCreateRequest();
        request.setNickname("测试考生");
        request.setUsername("kaosheng04");
        request.setPassword(CryptoUtils.md5("12345678"));
        request.setOrganization("测试班");

        Long enrollmentId = service.create(10L, request);

        assertEquals(40L, enrollmentId);
        ArgumentCaptor<RegisterRequest> registerCaptor = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(accountService).register(registerCaptor.capture());
        RegisterRequest register = registerCaptor.getValue();
        assertTrue(register.getCreateTenant());
        assertEquals("kaosheng04@exam.local", register.getEmail());
        assertEquals(CryptoUtils.md5("12345678"), register.getPassword());

        ArgumentCaptor<ExamEnrollment> enrollmentCaptor = ArgumentCaptor.forClass(ExamEnrollment.class);
        verify(enrollmentMapper).insert(enrollmentCaptor.capture());
        ExamEnrollment enrollment = enrollmentCaptor.getValue();
        assertEquals(10L, enrollment.getCampaignId());
        assertEquals(20L, enrollment.getAccountId());
        assertEquals(30L, enrollment.getTenantId());
        assertEquals("REGISTERED", enrollment.getStatus());
    }

    @Test
    void updatesAccountAndEnrollmentTogether() {
        ExamEnrollment enrollment = enrollment(11L, 10L, 20L);
        Account account = account(20L, "old-user", true);
        when(enrollmentMapper.selectOne(any())).thenReturn(enrollment);
        when(accountMapper.selectById(20L)).thenReturn(account);
        when(accountMapper.selectCount(any())).thenReturn(0L);
        when(accountMapper.updateById(any(Account.class))).thenReturn(1);
        when(enrollmentMapper.updateById(any(ExamEnrollment.class))).thenReturn(1);

        ExamCandidateAccountUpdateRequest request = new ExamCandidateAccountUpdateRequest();
        request.setNickname("新姓名");
        request.setUsername("new-user");
        request.setEmail("new-user@example.com");
        request.setOrganization("新班级");

        assertTrue(service.update(10L, 11L, request));
        assertEquals("new-user", account.getUsername());
        assertEquals("新姓名", enrollment.getName());
        assertEquals("新班级", enrollment.getOrganization());
    }

    @Test
    void rejectsPlainTextPasswordPayload() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.changePassword(10L, 11L, "12345678"));
        assertEquals("密码格式不正确", error.getMessage());
    }

    @Test
    void resetsPasswordUsingFrontendCompatibleDigest() {
        when(enrollmentMapper.selectOne(any())).thenReturn(enrollment(11L, 10L, 20L));
        when(accountMapper.selectById(20L)).thenReturn(account(20L, "candidate", true));
        when(accountService.adminChangePassword(20L, CryptoUtils.md5("12345678"))).thenReturn(true);

        assertTrue(service.resetPassword(10L, 11L));

        verify(accountService).adminChangePassword(20L, CryptoUtils.md5("12345678"));
    }

    @Test
    void removalKeepsHistoryAndDisablesUnusedAccount() {
        ExamEnrollment enrollment = enrollment(11L, 10L, 20L);
        Account account = account(20L, "candidate", true);
        when(enrollmentMapper.selectOne(any())).thenReturn(enrollment);
        when(accountMapper.selectById(20L)).thenReturn(account);
        when(enrollmentMapper.updateById(any(ExamEnrollment.class))).thenReturn(1);
        when(enrollmentMapper.selectCount(any())).thenReturn(0L);
        when(accountMapper.updateById(any(Account.class))).thenReturn(1);

        assertTrue(service.remove(10L, 11L));

        assertFalse(enrollment.getEnabled());
        assertEquals("REMOVED", enrollment.getStatus());
        assertFalse(account.getEnabled());
        verify(enrollmentMapper).updateById(enrollment);
        verify(accountMapper).updateById(account);
    }

    private ExamEnrollment enrollment(Long id, Long campaignId, Long accountId) {
        ExamEnrollment enrollment = new ExamEnrollment();
        enrollment.setId(id);
        enrollment.setCampaignId(campaignId);
        enrollment.setAccountId(accountId);
        enrollment.setEnabled(true);
        enrollment.setStatus("REGISTERED");
        return enrollment;
    }

    private Account account(Long id, String username, boolean enabled) {
        Account account = new Account();
        account.setId(id);
        account.setUsername(username);
        account.setEnabled(enabled);
        return account;
    }
}
