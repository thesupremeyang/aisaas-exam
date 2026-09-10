package com.hxh.apboa.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.hxh.apboa.assessment.mapper.ExamCampaignMapper;
import com.hxh.apboa.assessment.mapper.ExamEnrollmentMapper;
import com.hxh.apboa.assessment.service.ExamCandidateModelConfigService;
import com.hxh.apboa.assessment.service.ExamCampaignTiming;
import com.hxh.apboa.common.dto.ExamCandidateModelConfigRequest;
import com.hxh.apboa.common.entity.ExamCampaign;
import com.hxh.apboa.common.entity.ExamEnrollment;
import com.hxh.apboa.common.entity.ModelConfig;
import com.hxh.apboa.common.entity.ModelProvider;
import com.hxh.apboa.common.enums.AuthType;
import com.hxh.apboa.common.enums.ModelProviderType;
import com.hxh.apboa.common.vo.ExamCandidateModelConfigVO;
import com.hxh.apboa.model.mapper.ModelConfigMapper;
import com.hxh.apboa.model.mapper.ModelProviderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 考试场景的模型配置仅归属当前考生和当前批次，开考后由服务端锁定。
 * 预设与 SaaS 新版的模型服务商接入表保持一致，避免考生手工填写 Base URL。
 */
@Service
@RequiredArgsConstructor
public class ExamCandidateModelConfigServiceImpl implements ExamCandidateModelConfigService {

    private static final String CUSTOM_PROVIDER_KEY = "custom";
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private static final List<ProviderPreset> PRESETS = List.of(
            preset("deepseek", "official", "DeepSeek", ModelProviderType.OPEN_AI, "https://api.deepseek.com"),
            preset("mimo", "payg", "小米 MiMo", ModelProviderType.OPEN_AI, "https://api.xiaomimimo.com/v1"),
            preset("mimo", "token-plan", "小米 MiMo", ModelProviderType.OPEN_AI, "https://token-plan-cn.xiaomimimo.com/v1"),
            preset("volcengine", "ark", "火山方舟", ModelProviderType.OPEN_AI, "https://ark.cn-beijing.volces.com/api/v3"),
            preset("volcengine", "coding-plan", "火山方舟", ModelProviderType.OPEN_AI, "https://ark.cn-beijing.volces.com/api/coding/v3"),
            preset("moonshot", "official", "Kimi", ModelProviderType.OPEN_AI, "https://api.moonshot.cn/v1"),
            preset("moonshot", "kimi-code", "Kimi", ModelProviderType.OPEN_AI, "https://api.kimi.com/coding/v1"),
            preset("dashscope", "dashscope", "阿里云百炼", ModelProviderType.DASH_SCOPE, "https://dashscope.aliyuncs.com"),
            preset("dashscope", "compatible", "阿里云百炼", ModelProviderType.OPEN_AI, "https://dashscope.aliyuncs.com/compatible-mode/v1"),
            preset("dashscope", "coding-plan", "阿里云百炼", ModelProviderType.OPEN_AI, "https://coding.dashscope.aliyuncs.com/v1"),
            preset("dashscope", "token-plan", "阿里云百炼", ModelProviderType.OPEN_AI, "https://token-plan.cn-beijing.maas.aliyuncs.com/compatible-mode/v1"),
            preset("zhipu", "official", "智谱 AI", ModelProviderType.OPEN_AI, "https://open.bigmodel.cn/api/paas/v4"),
            preset("zhipu", "coding-plan", "智谱 AI", ModelProviderType.OPEN_AI, "https://open.bigmodel.cn/api/coding/paas/v4"),
            preset("qianfan", "official", "百度千帆", ModelProviderType.OPEN_AI, "https://qianfan.baidubce.com/v2"),
            preset("qianfan", "coding-plan", "百度千帆", ModelProviderType.OPEN_AI, "https://qianfan.baidubce.com/v2/coding"),
            preset("hunyuan", "official", "腾讯混元", ModelProviderType.OPEN_AI, "https://api.hunyuan.cloud.tencent.com/v1"),
            preset("hunyuan", "tokenhub-coding", "腾讯混元", ModelProviderType.OPEN_AI, "https://api.lkeap.cloud.tencent.com/coding/v3"),
            preset("spark", "official", "讯飞星火", ModelProviderType.OPEN_AI, "https://spark-api-open.xf-yun.com/v1"),
            preset("spark", "astron-coding-plan", "讯飞星火", ModelProviderType.OPEN_AI, "https://maas-coding-api.cn-huabei-1.xf-yun.com/v2"),
            preset("minimax", "official", "MiniMax", ModelProviderType.OPEN_AI, "https://api.minimaxi.com/v1"),
            preset("minimax", "token-plan", "MiniMax", ModelProviderType.ANTHROPIC, "https://api.minimaxi.com/anthropic"),
            preset("siliconflow", "official", "硅基流动", ModelProviderType.OPEN_AI, "https://api.siliconflow.cn/v1"),
            preset("stepfun", "step-plan", "阶跃星辰 StepFun", ModelProviderType.ANTHROPIC, "https://api.stepfun.com/step_plan"),
            preset("longcat", "official", "美团 LongCat", ModelProviderType.ANTHROPIC, "https://api.longcat.chat/anthropic"),
            preset("bailing", "official", "蚂蚁百灵", ModelProviderType.ANTHROPIC, "https://api.tbox.cn/api/anthropic"),
            preset("modelscope", "inference", "魔搭 ModelScope", ModelProviderType.ANTHROPIC, "https://api-inference.modelscope.cn"),
            preset("ollama", "local", "Ollama", ModelProviderType.OLLAMA, "http://localhost:11434")
    );

    private final ExamCampaignMapper campaignMapper;
    private final ExamEnrollmentMapper enrollmentMapper;
    private final ModelProviderMapper providerMapper;
    private final ModelConfigMapper configMapper;

    @Override
    public ExamCandidateModelConfigVO get(Long campaignId, Long accountId) {
        ExamEnrollment enrollment = findEnrollment(campaignId, accountId);
        if (enrollment == null || enrollment.getModelConfigId() == null) return null;
        ModelConfig config = configMapper.selectById(enrollment.getModelConfigId());
        ModelProvider provider = config == null ? null : providerMapper.selectById(config.getProviderId());
        return provider == null ? null : toView(config, provider);
    }

    @Override
    public List<String> listAvailableModels(Long campaignId, Long accountId, ExamCandidateModelConfigRequest request) {
        ExamEnrollment enrollment = ensureWaitingEnrollment(campaignId, accountId);
        ProviderInput input = resolveInput(request, false);
        String apiKey = request == null ? null : request.getApiKey();
        if (!StringUtils.hasText(apiKey) && enrollment.getModelConfigId() != null) {
            ModelConfig config = configMapper.selectById(enrollment.getModelConfigId());
            ModelProvider provider = config == null ? null : providerMapper.selectById(config.getProviderId());
            if (provider != null && provider.getType() == input.type() && input.baseUrl().equals(provider.getBaseUrl())) {
                apiKey = provider.getApiKey();
            }
        }
        return fetchModelIds(input, apiKey);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamCandidateModelConfigVO save(Long campaignId, Long accountId, ExamCandidateModelConfigRequest request) {
        ExamEnrollment enrollment = ensureWaitingEnrollment(campaignId, accountId);
        ProviderInput input = resolveInput(request, true);
        ModelConfig config = enrollment.getModelConfigId() == null ? null : configMapper.selectById(enrollment.getModelConfigId());
        ModelProvider provider = config == null ? null : providerMapper.selectById(config.getProviderId());
        boolean isNew = provider == null || config == null;
        if (isNew && (request == null || !StringUtils.hasText(request.getApiKey()))) throw new RuntimeException("请填写 API Key");
        if (isNew) {
            provider = new ModelProvider();
            provider.setAuthType(AuthType.CONFIG);
            provider.setEnabled(true);
            provider.setDescription("考生专属考试模型，请勿在管理端修改");
            config = createModelConfig();
        }
        provider.setType(input.type());
        provider.setName("考试模型-" + campaignId + "-" + accountId);
        provider.setBaseUrl(input.baseUrl());
        if (request != null && StringUtils.hasText(request.getApiKey())) provider.setApiKey(request.getApiKey().trim());
        if (!StringUtils.hasText(provider.getApiKey()) && input.type() != ModelProviderType.OLLAMA) throw new RuntimeException("请填写 API Key");
        if (isNew) {
            providerMapper.insert(provider);
            config.setProviderId(provider.getId());
            fillConfig(config, input);
            configMapper.insert(config);
            enrollment.setModelConfigId(config.getId());
            enrollmentMapper.updateById(enrollment);
        } else {
            providerMapper.updateById(provider);
            fillConfig(config, input);
            configMapper.updateById(config);
        }
        return toView(config, provider);
    }

    private ExamEnrollment ensureWaitingEnrollment(Long campaignId, Long accountId) {
        ExamCampaign campaign = campaignMapper.selectById(campaignId);
        if (campaign == null) throw new RuntimeException("考试批次不存在");
        if (ExamCampaignTiming.state(campaign, LocalDateTime.now()) != ExamCampaignTiming.State.WAITING) {
            throw new RuntimeException("仅可在开考前配置大模型");
        }
        ExamEnrollment enrollment = findEnrollment(campaignId, accountId);
        if (enrollment == null) throw new RuntimeException("当前账号未加入本场考试");
        return enrollment;
    }

    private ProviderInput resolveInput(ExamCandidateModelConfigRequest request, boolean requireModelId) {
        if (request == null || !StringUtils.hasText(request.getProviderKey())) throw new RuntimeException("请选择模型服务商");
        String providerKey = request.getProviderKey().trim();
        String schemeId = StringUtils.hasText(request.getSchemeId()) ? request.getSchemeId().trim() : "official";
        if (CUSTOM_PROVIDER_KEY.equals(providerKey)) {
            if (!StringUtils.hasText(request.getBaseUrl())) throw new RuntimeException("请填写 OpenAI 兼容接口地址");
            return new ProviderInput(providerKey, "custom", "OpenAI 兼容", ModelProviderType.OPEN_AI,
                    request.getBaseUrl().trim(), modelId(request, requireModelId));
        }
        ProviderPreset preset = PRESETS.stream().filter(item -> item.providerKey().equals(providerKey)
                && item.schemeId().equals(schemeId)).findFirst()
                .orElseThrow(() -> new RuntimeException("服务商接入方案无效"));
        return new ProviderInput(preset.providerKey(), preset.schemeId(), preset.providerName(), preset.type(),
                preset.baseUrl(), modelId(request, requireModelId));
    }

    private String modelId(ExamCandidateModelConfigRequest request, boolean required) {
        if (!StringUtils.hasText(request.getModelId())) {
            if (required) throw new RuntimeException("请选择或填写模型 ID");
            return null;
        }
        return request.getModelId().trim();
    }

    private List<String> fetchModelIds(ProviderInput input, String apiKey) {
        if (input.type() != ModelProviderType.OLLAMA && !StringUtils.hasText(apiKey)) throw new RuntimeException("请先填写 API Key");
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(modelListEndpoint(input)))
                .GET().timeout(Duration.ofSeconds(15)).header("Accept", "application/json");
        if (input.type() == ModelProviderType.ANTHROPIC) {
            request.header("x-api-key", apiKey).header("anthropic-version", "2023-06-01");
        } else if (input.type() != ModelProviderType.OLLAMA) {
            request.header("Authorization", "Bearer " + apiKey);
        }
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("获取模型失败（HTTP " + response.statusCode() + "），请检查 API Key 和接入方案");
            }
            return parseModelIds(response.body());
        } catch (IOException e) {
            throw new RuntimeException("无法连接模型服务，请检查 API 地址和网络", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取模型已取消", e);
        }
    }

    private String modelListEndpoint(ProviderInput input) {
        String baseUrl = input.baseUrl().replaceAll("/+$", "");
        URI uri = URI.create(baseUrl);
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new RuntimeException("API 基础地址仅支持 HTTP 或 HTTPS");
        }
        if (input.type() == ModelProviderType.OLLAMA) return baseUrl + "/api/tags";
        if (input.type() == ModelProviderType.DASH_SCOPE && !baseUrl.contains("compatible-mode")) return baseUrl + "/compatible-mode/v1/models";
        if (input.type() == ModelProviderType.ANTHROPIC) return baseUrl + "/v1/models";
        return baseUrl + "/models";
    }

    private List<String> parseModelIds(String body) {
        try {
            JsonNode root = JSON_MAPPER.readTree(body);
            JsonNode models = root.path("data");
            if (!models.isArray()) models = root.path("models");
            if (!models.isArray()) throw new RuntimeException("模型服务返回的数据格式不支持");
            LinkedHashSet<String> modelIds = new LinkedHashSet<>();
            for (JsonNode model : models) {
                String modelId = model.path("id").asText();
                if (modelId.isBlank()) modelId = model.path("name").asText();
                if (!modelId.isBlank()) modelIds.add(modelId);
            }
            if (modelIds.isEmpty()) throw new RuntimeException("该 API 未返回可用模型");
            return List.copyOf(modelIds);
        } catch (IOException e) {
            throw new RuntimeException("解析模型列表失败", e);
        }
    }

    private ModelConfig createModelConfig() {
        ModelConfig config = new ModelConfig();
        config.setModelType(JsonNodeFactory.instance.arrayNode().add("CHAT"));
        config.setStreaming(true);
        config.setThinking(false);
        config.setContextWindow(32768);
        config.setMaxTokens(4096);
        config.setTemperature(0.7D);
        config.setTopP(0.9D);
        config.setTopK(40);
        config.setRepeatPenalty(1.1D);
        config.setEnabled(true);
        config.setConnectivityStatus("NOT_CHECKED");
        return config;
    }

    private void fillConfig(ModelConfig config, ProviderInput input) {
        config.setName(input.modelId());
        config.setModelId(input.modelId());
        config.setDescription(input.providerName() + " · 考生专属配置");
        config.setConnectivityStatus("NOT_CHECKED");
        config.setConnectivityMessage(null);
        config.setLastConnectivityCheck(null);
    }

    private ExamEnrollment findEnrollment(Long campaignId, Long accountId) {
        return enrollmentMapper.selectOne(new LambdaQueryWrapper<ExamEnrollment>()
                .eq(ExamEnrollment::getCampaignId, campaignId).eq(ExamEnrollment::getAccountId, accountId));
    }

    private ExamCandidateModelConfigVO toView(ModelConfig config, ModelProvider provider) {
        ProviderPreset preset = PRESETS.stream().filter(item -> item.type() == provider.getType()
                && item.baseUrl().equals(provider.getBaseUrl())).findFirst().orElse(null);
        ExamCandidateModelConfigVO view = new ExamCandidateModelConfigVO();
        view.setConfigId(config.getId());
        view.setProviderKey(preset == null ? CUSTOM_PROVIDER_KEY : preset.providerKey());
        view.setSchemeId(preset == null ? "custom" : preset.schemeId());
        view.setProviderName(preset == null ? "OpenAI 兼容" : preset.providerName());
        view.setModelId(config.getModelId());
        view.setBaseUrl(provider.getBaseUrl());
        view.setApiKeyConfigured(StringUtils.hasText(provider.getApiKey()));
        view.setConnectivityStatus(config.getConnectivityStatus());
        view.setConnectivityMessage(config.getConnectivityMessage());
        view.setLastConnectivityCheck(config.getLastConnectivityCheck());
        return view;
    }

    private static ProviderPreset preset(String providerKey, String schemeId, String providerName,
                                         ModelProviderType type, String baseUrl) {
        return new ProviderPreset(providerKey, schemeId, providerName, type, baseUrl);
    }

    private record ProviderPreset(String providerKey, String schemeId, String providerName,
                                  ModelProviderType type, String baseUrl) {
    }

    private record ProviderInput(String providerKey, String schemeId, String providerName,
                                 ModelProviderType type, String baseUrl, String modelId) {
    }
}
