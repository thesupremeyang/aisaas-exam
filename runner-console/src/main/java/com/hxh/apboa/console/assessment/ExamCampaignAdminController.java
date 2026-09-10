package com.hxh.apboa.console.assessment;

import com.hxh.apboa.assessment.service.ExamCampaignAdminService;
import com.hxh.apboa.common.dto.ExamCampaignScheduleRequest;
import com.hxh.apboa.common.dto.ExamCampaignUpdateRequest;
import com.hxh.apboa.common.entity.ExamCampaign;
import com.hxh.apboa.common.exception.NotAuthException;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.common.util.UserUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 平台 admin 专用的考试批次管理接口。 */
@RestController
@RequestMapping("/exam/admin/campaigns")
@RequiredArgsConstructor
public class ExamCampaignAdminController {
    private final ExamCampaignAdminService campaignAdminService;

    @GetMapping
    public R<List<ExamCampaign>> list() {
        requirePlatformAdmin();
        return R.data(campaignAdminService.listCampaigns());
    }

    @PostMapping
    public R<ExamCampaign> create(@RequestBody ExamCampaignUpdateRequest request) {
        requirePlatformAdmin();
        return R.data(campaignAdminService.createCampaign(request));
    }

    @PutMapping("/{id}")
    public R<ExamCampaign> update(@PathVariable Long id,
                                  @RequestBody ExamCampaignUpdateRequest request) {
        requirePlatformAdmin();
        return R.data(campaignAdminService.updateCampaign(id, request));
    }

    @PatchMapping("/{id}/enabled")
    public R<ExamCampaign> updateEnabled(@PathVariable Long id,
                                         @RequestParam("enabled") Boolean enabled) {
        requirePlatformAdmin();
        return R.data(campaignAdminService.updateEnabled(id, enabled));
    }

    @PatchMapping("/{id}/score-query")
    public R<ExamCampaign> updateScoreQuery(@PathVariable Long id,
                                             @RequestParam("enabled") Boolean enabled) {
        requirePlatformAdmin();
        return R.data(campaignAdminService.updateScoreQueryEnabled(id, enabled));
    }

    @PutMapping("/{id}/schedule")
    public R<ExamCampaign> updateSchedule(@PathVariable Long id,
                                          @RequestBody ExamCampaignScheduleRequest request) {
        requirePlatformAdmin();
        return R.data(campaignAdminService.updateSchedule(id, request));
    }

    @PostMapping("/{id}/start-now")
    public R<ExamCampaign> startNow(@PathVariable Long id) {
        requirePlatformAdmin();
        return R.data(campaignAdminService.startNow(id));
    }

    private void requirePlatformAdmin() {
        if (!"admin".equalsIgnoreCase(UserUtils.getAccount())) {
            throw new NotAuthException("仅平台 admin 可管理考试时间");
        }
    }
}
