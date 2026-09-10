package com.hxh.apboa.console.assessment;

import com.hxh.apboa.assessment.service.ExamCandidateStatusService;
import com.hxh.apboa.common.exception.NotAuthException;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.common.util.UserUtils;
import com.hxh.apboa.common.vo.ExamCandidateStatusSnapshotVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 平台 admin 专用的考生状态监控接口。 */
@RestController
@RequestMapping("/exam/admin/campaigns")
@RequiredArgsConstructor
public class ExamCandidateStatusController {
    private final ExamCandidateStatusService candidateStatusService;

    @GetMapping("/{id}/candidates/status")
    public R<ExamCandidateStatusSnapshotVO> snapshot(@PathVariable Long id) {
        requirePlatformAdmin();
        return R.data(candidateStatusService.getSnapshot(id));
    }

    private void requirePlatformAdmin() {
        if (!"admin".equalsIgnoreCase(UserUtils.getAccount())) {
            throw new NotAuthException("仅平台 admin 可查看考生状态");
        }
    }
}
