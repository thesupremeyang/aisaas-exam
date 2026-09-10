package com.hxh.apboa.console.assessment;

import com.hxh.apboa.assessment.service.CandidateExamService;
import com.hxh.apboa.assessment.service.ExamCandidateModelConfigService;
import com.hxh.apboa.assessment.service.ExamQuestionService;
import com.hxh.apboa.common.dto.ExamCandidateModelConfigRequest;
import com.hxh.apboa.common.entity.ExamCampaign;
import com.hxh.apboa.common.entity.ExamQuestion;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.common.util.UserUtils;
import com.hxh.apboa.common.vo.ExamSsoTicketIssueVO;
import com.hxh.apboa.common.vo.ExamCandidateModelConfigVO;
import com.hxh.apboa.common.vo.ExamCandidateScoreVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 学员端考试门户接口。
 *
 * <p>提供考试批次列表、考试进度、我的智能体列表与 SSO 凭证签发。
 */
@RestController
@RequestMapping("/exam/candidate")
@RequiredArgsConstructor
public class CandidatePortalController {

    private final CandidateExamService candidateExamService;
    private final ExamCandidateModelConfigService candidateModelConfigService;
    private final ExamQuestionService examQuestionService;

    /** 列出进行中的考试批次。 */
    @GetMapping("/campaigns/active")
    public R<List<ExamCampaign>> listActiveCampaigns() {
        return R.data(candidateExamService.listActiveCampaigns(UserUtils.getId()));
    }

    /** 当前用户在指定批次下的考试提交进度。 */
    @GetMapping("/progress")
    public R<CandidateExamService.CandidateProgress> getProgress(@RequestParam("campaignId") Long campaignId) {
        return R.data(candidateExamService.getProgress(campaignId, UserUtils.getId()));
    }

    /** 查分模式下返回当前考生的本人成绩。 */
    @GetMapping("/score")
    public R<ExamCandidateScoreVO> getScore(@RequestParam("campaignId") Long campaignId) {
        return R.data(candidateExamService.getScore(campaignId, UserUtils.getId()));
    }

    /** 指定批次的试题列表（考生可见）。 */
    @GetMapping("/questions")
    public R<List<ExamQuestion>> listQuestions(@RequestParam("campaignId") Long campaignId) {
        candidateExamService.requireEnrollment(campaignId, UserUtils.getId());
        return R.data(examQuestionService.listByCampaign(campaignId));
    }

    @GetMapping("/model-config")
    public R<ExamCandidateModelConfigVO> getModelConfig(@RequestParam("campaignId") Long campaignId) {
        return R.data(candidateModelConfigService.get(campaignId, UserUtils.getId()));
    }

    @PutMapping("/model-config")
    public R<ExamCandidateModelConfigVO> saveModelConfig(@RequestParam("campaignId") Long campaignId,
                                                           @RequestBody ExamCandidateModelConfigRequest request) {
        return R.data(candidateModelConfigService.save(campaignId, UserUtils.getId(), request));
    }

    @PostMapping("/model-config/models")
    public R<List<String>> listAvailableModels(@RequestParam("campaignId") Long campaignId,
                                                @RequestBody ExamCandidateModelConfigRequest request) {
        return R.data(candidateModelConfigService.listAvailableModels(campaignId, UserUtils.getId(), request));
    }

    /** 为制作智能体签发一次性 SSO 凭证，回跳到平台我的智能体页。 */
    @GetMapping("/sso-ticket")
    public R<ExamSsoTicketIssueVO> issueSsoTicket(
        @RequestParam("campaignId") Long campaignId,
        @RequestParam("returnUrl") String returnUrl) {
        return R.data(candidateExamService.issueSsoTicket(campaignId, returnUrl, UserUtils.getId(), TenantUtils.getCurrentTenantId()));
    }
}
