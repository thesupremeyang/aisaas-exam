package com.hxh.apboa.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hxh.apboa.agent.service.AgentDefinitionService;
import com.hxh.apboa.knowledge.service.AgentKnowledgeBaseService;
import com.hxh.apboa.knowledge.service.KnowledgeBaseConfigService;
import com.hxh.apboa.mcp.service.AgentMcpServerService;
import com.hxh.apboa.mcp.service.McpServerService;
import com.hxh.apboa.model.service.ModelConfigService;
import com.hxh.apboa.assessment.mapper.ExamEnrollmentMapper;
import com.hxh.apboa.assessment.mapper.ExamCampaignMapper;
import com.hxh.apboa.assessment.mapper.ExamSsoTicketMapper;
import com.hxh.apboa.assessment.service.ExamCampaignTiming;
import com.hxh.apboa.assessment.service.ExamCampaignScoreMode;
import com.hxh.apboa.assessment.service.ExamSsoReturnUrlPolicy;
import com.hxh.apboa.assessment.service.ExamSsoService;
import com.hxh.apboa.assessment.service.ExamSsoTicketFactory;
import com.hxh.apboa.assessment.service.ExamSsoTicketState;
import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.entity.ExamCampaign;
import com.hxh.apboa.common.entity.ExamEnrollment;
import com.hxh.apboa.common.entity.ExamSsoTicket;
import com.hxh.apboa.common.entity.ModelConfig;
import com.hxh.apboa.common.exception.BusinessException;
import com.hxh.apboa.common.vo.ExamAgentSummaryVO;
import com.hxh.apboa.common.vo.ExamSsoTicketConsumeVO;
import com.hxh.apboa.common.vo.ExamSsoTicketIssueVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ExamSsoServiceImpl implements ExamSsoService {
    private static final int MAX_CANDIDATE_AGENTS = 100;

    private final ExamSsoTicketMapper ticketMapper;
    private final ExamEnrollmentMapper enrollmentMapper;
    private final ExamCampaignMapper campaignMapper;
    private final AgentDefinitionService agentDefinitionService;
    private final ModelConfigService modelConfigService;
    private final AgentMcpServerService agentMcpServerService;
    private final McpServerService mcpServerService;
    private final AgentKnowledgeBaseService agentKnowledgeBaseService;
    private final KnowledgeBaseConfigService knowledgeBaseConfigService;
    private final ExamSsoReturnUrlPolicy returnUrlPolicy;
    private final ExamSsoTicketFactory ticketFactory = new ExamSsoTicketFactory();

    public ExamSsoServiceImpl(ExamSsoTicketMapper ticketMapper,
                              ExamEnrollmentMapper enrollmentMapper,
                              ExamCampaignMapper campaignMapper,
                              AgentDefinitionService agentDefinitionService,
                              ModelConfigService modelConfigService,
                              AgentMcpServerService agentMcpServerService,
                              McpServerService mcpServerService,
                              AgentKnowledgeBaseService agentKnowledgeBaseService,
                              KnowledgeBaseConfigService knowledgeBaseConfigService,
                              @Value("${exam.sso.return-url-allowlist:}") String allowedReturnUrlOrigins) {
        this.ticketMapper = ticketMapper;
        this.enrollmentMapper = enrollmentMapper;
        this.campaignMapper = campaignMapper;
        this.agentDefinitionService = agentDefinitionService;
        this.modelConfigService = modelConfigService;
        this.agentMcpServerService = agentMcpServerService;
        this.mcpServerService = mcpServerService;
        this.agentKnowledgeBaseService = agentKnowledgeBaseService;
        this.knowledgeBaseConfigService = knowledgeBaseConfigService;
        this.returnUrlPolicy = new ExamSsoReturnUrlPolicy(parseAllowedOrigins(allowedReturnUrlOrigins));
    }

    @Override
    public ExamSsoTicketIssueVO issueTicket(Long campaignId, String returnUrl, Long accountId, Long tenantId) {
        requireEnrollment(campaignId, accountId, tenantId);
        requireSubmissionWindow(campaignId);
        returnUrlPolicy.validate(returnUrl);
        var issued = ticketFactory.issue(tenantId, accountId, campaignId, returnUrl, LocalDateTime.now());
        ticketMapper.insert(issued.ticket());
        return new ExamSsoTicketIssueVO(issued.rawTicket(), issued.ticket().getExpiresAt(), returnUrl);
    }

    @Override
    public ExamSsoTicketConsumeVO consumeTicket(String rawTicket, Long accountId, Long tenantId) {
        if (rawTicket == null || rawTicket.isBlank()) {
            throw new BusinessException("SSO 凭证不能为空");
        }
        var now = LocalDateTime.now();
        ExamSsoTicket ticket = ticketMapper.selectOne(new LambdaQueryWrapper<ExamSsoTicket>()
                .eq(ExamSsoTicket::getTenantId, tenantId)
                .eq(ExamSsoTicket::getTicketHash, ticketFactory.hash(rawTicket)));
        try {
            ExamSsoTicketState.assertConsumable(ticket, now);
        } catch (IllegalStateException exception) {
            throw new BusinessException(exception.getMessage());
        }
        if (!ticket.getAccountId().equals(accountId)) {
            throw new BusinessException("无权消费该 SSO 凭证");
        }
        int changed = ticketMapper.update(null, new LambdaUpdateWrapper<ExamSsoTicket>()
                .set(ExamSsoTicket::getConsumedAt, now)
                .eq(ExamSsoTicket::getId, ticket.getId())
                .isNull(ExamSsoTicket::getConsumedAt)
                .gt(ExamSsoTicket::getExpiresAt, now));
        if (changed != 1) {
            throw new BusinessException("SSO 凭证已被使用或已过期");
        }
        return new ExamSsoTicketConsumeVO(ticket.getAccountId(), ticket.getTenantId(), ticket.getCampaignId(), ticket.getReturnUrl());
    }

    @Override
    public List<ExamAgentSummaryVO> listCandidateAgents(Long campaignId, Long accountId, Long tenantId) {
        requireEnrollment(campaignId, accountId, tenantId);
        requireSubmissionWindow(campaignId);
        return agentDefinitionService.list(new LambdaQueryWrapper<AgentDefinition>()
                        .eq(AgentDefinition::getTenantId, tenantId)
                        .eq(AgentDefinition::getEnabled, true)
                        .orderByDesc(AgentDefinition::getUpdatedAt)
                        .last("limit " + MAX_CANDIDATE_AGENTS))
                .stream()
                .map(this::toSafeSummary)
                .toList();
    }

    private void requireEnrollment(Long campaignId, Long accountId, Long tenantId) {
        if (campaignId == null || accountId == null || tenantId == null) {
            throw new BusinessException("考试批次或登录上下文无效");
        }
        ExamEnrollment enrollment = enrollmentMapper.selectOne(new LambdaQueryWrapper<ExamEnrollment>()
                .eq(ExamEnrollment::getCampaignId, campaignId)
                .eq(ExamEnrollment::getAccountId, accountId)
                .eq(ExamEnrollment::getTenantId, tenantId)
                .eq(ExamEnrollment::getEnabled, true)
                .eq(ExamEnrollment::getStatus, "REGISTERED"));
        if (enrollment == null) {
            throw new BusinessException("当前账号尚未初始化本次考试作品，请返回考试门户后重试");
        }
    }

    private void requireSubmissionWindow(Long campaignId) {
        ExamCampaign campaign = campaignMapper.selectById(campaignId);
        if (campaign == null || !Boolean.TRUE.equals(campaign.getEnabled())) {
            throw new BusinessException("考试批次不可用");
        }
        var state = ExamCampaignTiming.state(campaign, LocalDateTime.now());
        if (state == ExamCampaignTiming.State.WAITING) {
            throw new BusinessException("考试尚未开始，请在开考时间后进入智能体制作平台");
        }
        if (state == ExamCampaignTiming.State.ENDED) {
            if (!ExamCampaignScoreMode.isEnabled(campaign)) {
                throw new BusinessException("考试时间已结束，不能再进入智能体制作平台");
            }
        }
    }

    private ExamAgentSummaryVO toSafeSummary(AgentDefinition agent) {
        int promptLength = agent.getSystemPrompt() == null ? 0 : agent.getSystemPrompt().length();
        ModelConfig model = agent.getModelConfigId() == null ? null : modelConfigService.getById(agent.getModelConfigId());
        return new ExamAgentSummaryVO(
                agent.getId(), agent.getName(), agent.getDescription(),
                agent.getAgentType() == null ? null : agent.getAgentType().name(),
                agent.getVersion(), agent.getTag(), model == null ? null : model.getName(), promptLength,
                agentDefinitionService.getEnabledSkillsOfAgent(agent.getId()).stream().map(skill -> skill.getName()).sorted().toList(),
                names(agentMcpServerService.getMcpIds(agent.getId()), mcpServerService::listByIds),
                names(agentKnowledgeBaseService.getKnowledgeIds(agent.getId()), knowledgeBaseConfigService::listByIds),
                agent.getUpdatedAt());
    }

    private List<String> names(List<Long> ids, java.util.function.Function<List<Long>, List<? extends com.hxh.apboa.common.entity.BaseEntity>> finder) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return finder.apply(ids).stream()
                .map(entity -> switch (entity) {
                    case com.hxh.apboa.common.entity.McpServer mcpServer -> mcpServer.getName();
                    case com.hxh.apboa.common.entity.KnowledgeBaseConfig knowledgeBase -> knowledgeBase.getName();
                    default -> null;
                })
                .filter(java.util.Objects::nonNull)
                .sorted()
                .toList();
    }

    private static Set<String> parseAllowedOrigins(String configuredOrigins) {
        if (configuredOrigins == null || configuredOrigins.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
