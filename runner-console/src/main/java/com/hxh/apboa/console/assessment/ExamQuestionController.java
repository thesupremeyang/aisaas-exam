package com.hxh.apboa.console.assessment;

import com.hxh.apboa.assessment.service.ExamQuestionService;
import com.hxh.apboa.common.entity.ExamQuestion;
import com.hxh.apboa.common.exception.NotAuthException;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.common.util.UserUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/exam/admin/campaigns/{campaignId}/questions")
@RequiredArgsConstructor
public class ExamQuestionController {
    private final ExamQuestionService questionService;

    @GetMapping
    public R<List<ExamQuestion>> list(@PathVariable Long campaignId) {
        requirePlatformAdmin();
        return R.data(questionService.listByCampaign(campaignId));
    }

    @PostMapping
    public R<ExamQuestion> create(@PathVariable Long campaignId, @RequestBody ExamQuestion question) {
        requirePlatformAdmin();
        question.setCampaignId(campaignId);
        return R.data(questionService.create(question));
    }

    @PutMapping("/{id}")
    public R<ExamQuestion> update(@PathVariable Long id, @RequestBody ExamQuestion question) {
        requirePlatformAdmin();
        return R.data(questionService.update(id, question));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        requirePlatformAdmin();
        questionService.delete(id);
        return R.success("已删除");
    }

    private void requirePlatformAdmin() {
        if (!"admin".equalsIgnoreCase(UserUtils.getAccount())) {
            throw new NotAuthException("仅平台 admin 可管理试题");
        }
    }
}
