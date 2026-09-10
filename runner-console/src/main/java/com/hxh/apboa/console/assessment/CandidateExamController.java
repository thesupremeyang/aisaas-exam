package com.hxh.apboa.console.assessment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hxh.apboa.assessment.mapper.AssessmentTaskMapper;
import com.hxh.apboa.assessment.mapper.AssessmentReportMapper;
import com.hxh.apboa.assessment.service.CandidateExamService;
import com.hxh.apboa.assessment.service.ExamSubmissionService;
import com.hxh.apboa.common.entity.AssessmentTask;
import com.hxh.apboa.common.entity.AssessmentReport;
import com.hxh.apboa.common.entity.ExamSubmission;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.common.util.UserUtils;
import com.hxh.apboa.common.vo.AgentDefinitionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController @RequestMapping("/exam/candidate/submissions") @RequiredArgsConstructor
public class CandidateExamController {
    private final ExamSubmissionService submissionService;
    private final AssessmentTaskMapper taskMapper;
    private final AssessmentReportMapper reportMapper;
    private final CandidateExamService candidateService;

    @GetMapping("/{id}")
    public R<ExamSubmission> detail(@PathVariable Long id) { return R.data(submissionService.getOwned(id, UserUtils.getId())); }

    @PutMapping("/{id}/draft")
    public R<Boolean> saveDraft(@PathVariable Long id, @RequestBody ExamSubmission request) {
        return R.data(submissionService.saveDraft(id, request, UserUtils.getId()));
    }

    @PostMapping("/{id}/submit")
    public R<ExamSubmission> submit(@PathVariable Long id) { return R.data(submissionService.submit(id, UserUtils.getId())); }

    /** 查分模式下查看本人已提交作品所关联的智能体配置，只读。 */
    @GetMapping("/{id}/agent-config")
    public R<AgentDefinitionVO> agentConfiguration(@PathVariable Long id) {
        return R.data(candidateService.getSubmittedAgentConfiguration(id, UserUtils.getId()));
    }

    @PostMapping("/{id}/retry-assessment")
    public R<ExamSubmission> retryAssessment(@PathVariable Long id) {
        return R.data(submissionService.retryAssessment(id, UserUtils.getId()));
    }

    @GetMapping("/{id}/assessment-task")
    public R<AssessmentTask> task(@PathVariable Long id) {
        submissionService.getOwned(id, UserUtils.getId());
        return R.data(taskMapper.selectOne(new LambdaQueryWrapper<AssessmentTask>().eq(AssessmentTask::getSubmissionId, id).orderByDesc(AssessmentTask::getCreatedAt).last("limit 1")));
    }

    @GetMapping("/{id}/assessment-report")
    public R<AssessmentReport> report(@PathVariable Long id) {
        submissionService.getOwned(id, UserUtils.getId());
        return R.data(reportMapper.selectOne(new LambdaQueryWrapper<AssessmentReport>()
                .eq(AssessmentReport::getSubmissionId, id)
                .apply("del_flag = 0")
                .last("limit 1")));
    }

    @GetMapping("/enrollments/{enrollmentId}/submissions")
    public R<List<ExamSubmission>> listSubmissions(@PathVariable Long enrollmentId) {
        return R.data(candidateService.listSubmissions(enrollmentId, UserUtils.getId()));
    }

    @PostMapping("/enrollments/{enrollmentId}/submissions")
    public R<ExamSubmission> createSubmission(@PathVariable Long enrollmentId,
                                              @RequestBody(required = false) java.util.Map<String, Long> body) {
        Long questionId = body != null ? body.get("questionId") : null;
        return R.data(candidateService.createSubmission(enrollmentId, UserUtils.getId(), questionId));
    }

    @DeleteMapping("/{id}")
    public R<Boolean> deleteSubmission(@PathVariable Long id) {
        candidateService.deleteSubmission(id, UserUtils.getId());
        return R.data(true);
    }
}
