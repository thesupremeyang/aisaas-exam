package com.hxh.apboa.assessment.service;

import com.hxh.apboa.common.entity.ExamCampaign;
import com.hxh.apboa.common.entity.ExamSubmission;
import com.hxh.apboa.common.vo.ExamAgentSummaryVO;
import com.hxh.apboa.common.vo.ExamCandidateScoreVO;
import com.hxh.apboa.common.vo.AgentDefinitionVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 学员端考试门户业务服务。
 *
 * <p>提供学员视角下的考试批次列表、考试进度、我的智能体列表与 SSO 凭证签发。
 */
public interface CandidateExamService {

    /** 列出当前账号已报名且对考生开放的考试批次（按状态与时间窗过滤）。 */
    List<ExamCampaign> listActiveCampaigns(Long accountId);

    /** 校验当前账号是否已报名指定考试批次。 */
    void requireEnrollment(Long campaignId, Long accountId);

    /** 返回当前用户在指定批次下的提交进度。 */
    CandidateProgress getProgress(Long campaignId, Long accountId);

    /** 列出某报名下的全部作品提交（作品集），按创建时间升序。 */
    List<ExamSubmission> listSubmissions(Long enrollmentId, Long accountId);

    /** 在同一报名下新增一份草稿作品提交，返回新建的 submission。 */
    ExamSubmission createSubmission(Long enrollmentId, Long accountId, Long questionId);

    /** 删除指定作品提交（仅限本人、仅草稿），采用软删除。 */
    void deleteSubmission(Long submissionId, Long accountId);
    /** 列出当前用户在指定批次下可带入平台的智能体（脱敏）。 */
    List<ExamAgentSummaryVO> listMyAgents(Long campaignId, Long accountId, Long tenantId);

    /** 返回查分模式下当前考生的成绩与可回看的已提交作品。 */
    ExamCandidateScoreVO getScore(Long campaignId, Long accountId);

    /** 返回查分模式下当前考生提交作品所关联的智能体配置。 */
    AgentDefinitionVO getSubmittedAgentConfiguration(Long submissionId, Long accountId);

    /** 为指定批次签发一次性 SSO 凭证，回跳到平台我的智能体页。 */
    com.hxh.apboa.common.vo.ExamSsoTicketIssueVO issueSsoTicket(Long campaignId, String returnUrl, Long accountId, Long tenantId);

    /** 学员端门户进度聚合对象。 */
    record CandidateProgress(
            boolean enrolled,
            Long enrollmentId,
            Long submissionId,
            String submissionStatus,
            boolean aiPublished,
            boolean registrationOpen,
            boolean submissionOpen,
            boolean scoreQueryEnabled,
            int maxWorks,
            String examState,
            LocalDateTime examStartAt,
            LocalDateTime examEndAt,
            LocalDateTime serverNow
    ) {
    }
}
