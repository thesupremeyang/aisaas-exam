package com.hxh.apboa.console.assessment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hxh.apboa.assessment.dto.GradingDetailVO;
import com.hxh.apboa.assessment.dto.GradingOverviewVO;
import com.hxh.apboa.assessment.dto.GradingSettingsRequest;
import com.hxh.apboa.assessment.mapper.AiGradingSettingsMapper;
import com.hxh.apboa.assessment.service.ExamGradingAdminService;
import com.hxh.apboa.assessment.service.ExamGradingLlmClient;
import com.hxh.apboa.common.entity.AiGradingSettings;
import com.hxh.apboa.common.exception.BusinessException;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.common.util.UserUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 阅卷（配置评分）管理端接口。
 * 评分对象是考生智能体的真实配置（提示词/知识库/Skill 文件），结果独立于旧 AI 智评与人工复核，
 * 可由管理员按卷"采纳为复核得分"。
 * 模型配置（endpoint/API Key/模型）存 ai_grading_settings 表，系统设置保存后即时生效、无需重启；
 * 保存前强制连通性测试，测试失败不落库。
 */
@RestController
@RequestMapping("/exam/admin/grading")
@RequiredArgsConstructor
public class ExamGradingAdminController {

    private static final long SETTINGS_ID = 1L;

    private final ExamGradingAdminService gradingAdminService;
    private final ExamGradingLlmClient gradingLlmClient;
    private final AiGradingSettingsMapper settingsMapper;

    private void requirePlatformAdmin() {
        if (!UserUtils.isPlatformAdmin()) {
            throw new BusinessException("仅平台管理员可操作 AI 阅卷");
        }
    }

    /**
     * 触发全场次阅卷会消耗大量算力并写复核分，必须限定平台管理员。
     * 此前本组接口（run/rerun/overview/detail/adopt）未加校验，任何已登录账号（含考生）都能调用。
     */
    @PostMapping("/campaigns/{campaignId}/run")
    public R<Map<String, Integer>> start(@PathVariable Long campaignId) {
        requirePlatformAdmin();
        return R.data(gradingAdminService.startCampaignGrading(campaignId));
    }

    @PostMapping("/submissions/{submissionId}/rerun")
    public R<Boolean> rerun(@PathVariable Long submissionId) {
        requirePlatformAdmin();
        gradingAdminService.rerunSubmission(submissionId);
        return R.data(true);
    }

    @GetMapping("/campaigns/{campaignId}/overview")
    public R<GradingOverviewVO> overview(@PathVariable Long campaignId) {
        requirePlatformAdmin();
        return R.data(gradingAdminService.overview(campaignId));
    }

    @GetMapping("/submissions/{submissionId}/detail")
    public R<GradingDetailVO> detail(@PathVariable Long submissionId) {
        requirePlatformAdmin();
        return R.data(gradingAdminService.detail(submissionId));
    }

    /** 采纳会直接写入该卷的复核得分，属于写分操作，必须限定平台管理员。 */
    @PostMapping("/submissions/{submissionId}/adopt")
    public R<Boolean> adopt(@PathVariable Long submissionId) {
        requirePlatformAdmin();
        gradingAdminService.adopt(submissionId, UserUtils.getId());
        return R.data(true);
    }

    // ==================== 模型设置（系统设置） ====================

    /** API Key 脱敏：保留前 6 位与后 4 位 */
    private String maskKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 12) {
            return "****";
        }
        return apiKey.substring(0, 6) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    private AiGradingSettings currentSettings() {
        return settingsMapper.selectOne(new LambdaQueryWrapper<AiGradingSettings>()
                .eq(AiGradingSettings::getId, SETTINGS_ID)
                .last("limit 1"));
    }

    /** 读取当前模型配置（API Key 脱敏展示） */
    @GetMapping("/settings")
    public R<Map<String, Object>> getSettings() {
        requirePlatformAdmin();
        AiGradingSettings settings = currentSettings();
        Map<String, Object> result = new LinkedHashMap<>();
        if (settings != null) {
            result.put("endpoint", settings.getEndpoint());
            result.put("model", settings.getModel());
            result.put("apiKeyMasked", maskKey(settings.getApiKey()));
            result.put("apiKeyConfigured", true);
            result.put("enabled", Boolean.TRUE.equals(settings.getEnabled()));
        } else {
            result.put("endpoint", "");
            result.put("model", "");
            result.put("apiKeyMasked", "");
            result.put("apiKeyConfigured", false);
            result.put("enabled", false);
        }
        return R.data(result);
    }

    /** 连通性测试（不落库）：apiKey 留空时使用已保存的 Key（便于只改 endpoint/model 后测试） */
    @PostMapping("/settings/test")
    public R<Map<String, Object>> testSettings(@RequestBody GradingSettingsRequest request) {
        requirePlatformAdmin();
        String endpoint = request.getEndpoint() == null ? "" : request.getEndpoint().trim();
        String model = request.getModel() == null ? "" : request.getModel().trim();
        String apiKey = FuncUtils.isEmpty(request.getApiKey()) ? null : request.getApiKey().trim();
        if (FuncUtils.isEmpty(endpoint) || FuncUtils.isEmpty(model)) {
            throw new BusinessException("接口地址与模型名不能为空");
        }
        if (apiKey == null) {
            AiGradingSettings settings = currentSettings();
            apiKey = settings == null ? null : settings.getApiKey();
        }
        if (FuncUtils.isEmpty(apiKey)) {
            throw new BusinessException("API Key 不能为空");
        }
        return R.data(gradingLlmClient.testConnection(endpoint, apiKey, model));
    }

    /** 保存模型配置：保存前强制连通性测试，失败则不落库 */
    @PostMapping("/settings")
    public R<Map<String, Object>> saveSettings(@RequestBody GradingSettingsRequest request) {
        requirePlatformAdmin();
        String endpoint = request.getEndpoint() == null ? "" : request.getEndpoint().trim();
        String model = request.getModel() == null ? "" : request.getModel().trim();
        String apiKey = FuncUtils.isEmpty(request.getApiKey()) ? null : request.getApiKey().trim();
        boolean enabled = !Boolean.FALSE.equals(request.getEnabled());
        if (FuncUtils.isEmpty(endpoint) || FuncUtils.isEmpty(model)) {
            throw new BusinessException("接口地址与模型名不能为空");
        }

        AiGradingSettings settings = currentSettings();
        if (apiKey == null) {
            apiKey = settings == null ? null : settings.getApiKey();
        }
        if (FuncUtils.isEmpty(apiKey)) {
            throw new BusinessException("API Key 不能为空");
        }

        // 保存前强制测试：保证落库的配置可用
        Map<String, Object> test = gradingLlmClient.testConnection(endpoint, apiKey, model);
        if (!Boolean.TRUE.equals(test.get("success"))) {
            throw new BusinessException("连通性测试未通过，配置未保存：" + test.get("message"));
        }

        AiGradingSettings entity = new AiGradingSettings();
        entity.setId(SETTINGS_ID);
        entity.setEndpoint(endpoint);
        entity.setApiKey(apiKey);
        entity.setModel(model);
        entity.setEnabled(enabled);
        entity.setUpdatedBy(UserUtils.getId());
        if (settings == null) {
            settingsMapper.insert(entity);
        } else {
            settingsMapper.updateById(entity);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("saved", true);
        result.put("test", test);
        result.put("apiKeyMasked", maskKey(apiKey));
        return R.data(result, "模型配置已保存并生效");
    }

    /** 删除配置（回退到 yml/env 默认模型）——可选运维接口 */
    @PostMapping("/settings/reset")
    public R<Boolean> resetSettings(@RequestParam(value = "confirm", required = false) String confirm) {
        requirePlatformAdmin();
        if (!"yes".equals(confirm)) {
            throw new BusinessException("请携带 confirm=yes 确认重置");
        }
        settingsMapper.deleteById(SETTINGS_ID);
        return R.data(true);
    }
}
