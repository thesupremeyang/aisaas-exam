package com.hxh.apboa.assessment.service;

import com.hxh.apboa.common.vo.ExamAgentSummaryVO;
import com.hxh.apboa.common.vo.ExamSsoTicketConsumeVO;
import com.hxh.apboa.common.vo.ExamSsoTicketIssueVO;

import java.util.List;

public interface ExamSsoService {
    ExamSsoTicketIssueVO issueTicket(Long campaignId, String returnUrl, Long accountId, Long tenantId);

    ExamSsoTicketConsumeVO consumeTicket(String rawTicket, Long accountId, Long tenantId);

    List<ExamAgentSummaryVO> listCandidateAgents(Long campaignId, Long accountId, Long tenantId);
}
