package com.hxh.apboa.console.assessment;

import com.hxh.apboa.assessment.service.ExamSsoService;
import com.hxh.apboa.common.dto.ExamSsoTicketRequest;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.common.util.UserUtils;
import com.hxh.apboa.common.vo.ExamAgentSummaryVO;
import com.hxh.apboa.common.vo.ExamSsoTicketConsumeVO;
import com.hxh.apboa.common.vo.ExamSsoTicketIssueVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Authenticated, one-time hand-off between the examination portal and the agent studio.
 * These endpoints intentionally have no {@code @PassAuth}: the ticket alone is not enough
 * to read or establish a session for another account.
 */
@RestController
@RequestMapping("/exam")
@RequiredArgsConstructor
public class ExamSsoController {
    private final ExamSsoService examSsoService;

    @PostMapping("/sso/tickets")
    public R<ExamSsoTicketIssueVO> issueTicket(@RequestBody ExamSsoTicketRequest request) {
        return R.data(examSsoService.issueTicket(
                request.getCampaignId(), request.getReturnUrl(), UserUtils.getId(), UserUtils.getTenantId()));
    }

    @PostMapping("/sso/tickets/{ticket}/consume")
    public R<ExamSsoTicketConsumeVO> consumeTicket(@PathVariable String ticket) {
        return R.data(examSsoService.consumeTicket(ticket, UserUtils.getId(), UserUtils.getTenantId()));
    }

    @GetMapping("/candidate/agents")
    public R<List<ExamAgentSummaryVO>> candidateAgents(@RequestParam Long campaignId) {
        return R.data(examSsoService.listCandidateAgents(campaignId, UserUtils.getId(), UserUtils.getTenantId()));
    }
}
