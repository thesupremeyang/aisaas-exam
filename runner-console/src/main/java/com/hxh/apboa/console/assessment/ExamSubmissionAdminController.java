package com.hxh.apboa.console.assessment;

import com.hxh.apboa.assessment.dto.ExamSubmissionAdminVO;
import com.hxh.apboa.assessment.service.ExamSubmissionAdminService;
import com.hxh.apboa.common.exception.NotAuthException;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.common.util.UserUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 平台 admin 专用答卷管理接口。 */
@RestController
@RequestMapping("/exam/admin")
@RequiredArgsConstructor
public class ExamSubmissionAdminController {
    private final ExamSubmissionAdminService submissionAdminService;

    @GetMapping("/campaigns/{campaignId}/submissions")
    public R<List<ExamSubmissionAdminVO>> list(@PathVariable Long campaignId,
                                               @RequestParam(value = "name", required = false) String name) {
        requirePlatformAdmin();
        List<ExamSubmissionAdminVO> rows = submissionAdminService.listCampaignSubmissions(campaignId);
        if (name != null && !name.isBlank()) {
            String keyword = name.trim();
            rows = rows.stream()
                    .filter(row -> (row.getCandidateName() != null && row.getCandidateName().contains(keyword))
                            || (row.getOrganization() != null && row.getOrganization().contains(keyword)))
                    .toList();
        }
        return R.data(rows);
    }

    private void requirePlatformAdmin() {
        if (!UserUtils.isPlatformAdmin()) {
            throw new NotAuthException("仅平台 admin 可管理答卷");
        }
    }
}
