package com.hxh.apboa.engine.model.checkpiont;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hxh.apboa.common.dto.ModelConnectionCheckRequest;
import com.hxh.apboa.common.entity.ModelConfig;
import com.hxh.apboa.common.enums.AuthType;
import com.hxh.apboa.common.enums.ModelProviderType;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.common.wrapper.ModelConfigWrapper;
import com.hxh.apboa.common.wrapper.ModelWrapper;
import com.hxh.apboa.engine.model.ChatModelFactory;
import com.hxh.apboa.model.service.ModelConfigService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.Model;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 描述：
 *
 * @author huxuehao
 **/
@RestController
@RequestMapping("/runtime/model/config")
@RequiredArgsConstructor
public class CheckModelService {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private final ModelConfigService modelConfigService;
    private final ChatModelFactory chatModelFactory;

    @RequestMapping("/check/{modelId}")
    public R<CheckModelResult> checkModel(@PathVariable("modelId") Long modelId) {
        // 先更新状态为 CHECKING（独立事务，立即提交）
        updateConnectivityResult(modelId, "CHECKING", null);

        try {

            ModelWrapper config = modelConfigService.getModelWrapperById(modelId);

            ModelConfigWrapper configWrapper = new ModelConfigWrapper();
            config.getConfig().fillModelConfigWrapper(configWrapper);
            config.getProvider().fillModelConfigWrapper(configWrapper);

            CheckModelResult result = runConnectionCheck(configWrapper);
            updateConnectivityResult(modelId, result.success ? "CONNECTED" : "FAILED", result.message);
            return R.data(result);
        } catch (Exception e) {
            // 检测失败：异常
            updateConnectivityResult(modelId, "FAILED", e.getMessage());
            return R.data(new CheckModelResult(false, e.getMessage()));
        }
    }

    /**
     * 使用弹窗中尚未落库的供应商参数检测模型连接。
     */
    @PostMapping("/check")
    public R<CheckModelResult> checkModelConnection(@RequestBody ModelConnectionCheckRequest request) {
        try {
            if (request == null || request.getType() == null) {
                throw new IllegalArgumentException("请选择供应商");
            }
            if (!StringUtils.hasText(request.getBaseUrl())) {
                throw new IllegalArgumentException("请填写 Base URL");
            }
            if (!StringUtils.hasText(request.getModelId())) {
                throw new IllegalArgumentException("请填写模型名称或模型 ID");
            }

            ModelConfigWrapper configWrapper = new ModelConfigWrapper();
            configWrapper.setProvider(request.getType());
            configWrapper.setBaseUrl(request.getBaseUrl().trim());
            configWrapper.setModelCode(request.getModelId().trim());
            configWrapper.setApiKey(resolveApiKey(request));

            return R.data(runConnectionCheck(configWrapper));
        } catch (Exception e) {
            return R.data(new CheckModelResult(false, e.getMessage()));
        }
    }

    /**
     * 获取当前表单参数对应的模型列表，不保存任何配置。
     */
    @PostMapping("/models")
    public R<List<String>> listAvailableModels(@RequestBody ModelConnectionCheckRequest request) {
        try {
            validateConnectionRequest(request, false);
            String apiKey = resolveApiKey(request);
            String baseUrl = request.getBaseUrl().trim().replaceAll("/+$", "");
            String endpoint = modelListEndpoint(request.getType(), baseUrl);

            HttpRequest.Builder httpRequest = HttpRequest.newBuilder(URI.create(endpoint))
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json");
            if (request.getType() == ModelProviderType.ANTHROPIC) {
                httpRequest.header("x-api-key", apiKey).header("anthropic-version", "2023-06-01");
            } else if (request.getType() != ModelProviderType.OLLAMA) {
                httpRequest.header("Authorization", "Bearer " + apiKey);
            }

            HttpResponse<String> response = HTTP_CLIENT.send(
                    httpRequest.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("获取模型失败（HTTP " + response.statusCode() + "）");
            }
            return R.data(parseModelIds(response.body()));
        } catch (Exception e) {
            return R.fail(e.getMessage());
        }
    }

    private void validateConnectionRequest(ModelConnectionCheckRequest request, boolean requireModelId) {
        if (request == null || request.getType() == null) {
            throw new IllegalArgumentException("请选择供应商");
        }
        if (!StringUtils.hasText(request.getBaseUrl())) {
            throw new IllegalArgumentException("请填写 Base URL");
        }
        if (requireModelId && !StringUtils.hasText(request.getModelId())) {
            throw new IllegalArgumentException("请填写模型名称或模型 ID");
        }
    }

    private String modelListEndpoint(ModelProviderType type, String baseUrl) {
        URI uri = URI.create(baseUrl);
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("API 基础地址仅支持 HTTP 或 HTTPS");
        }
        if (type == ModelProviderType.OLLAMA) return baseUrl + "/api/tags";
        if (type == ModelProviderType.DASH_SCOPE && !baseUrl.contains("compatible-mode")) {
            return baseUrl + "/compatible-mode/v1/models";
        }
        if (type == ModelProviderType.ANTHROPIC) return baseUrl + "/v1/models";
        return baseUrl + "/models";
    }

    private List<String> parseModelIds(String body) {
        try {
            JsonNode root = JSON_MAPPER.readTree(body);
            JsonNode models = root.path("data");
            if (!models.isArray()) models = root.path("models");
            if (!models.isArray()) throw new RuntimeException("该服务商不支持模型列表格式");

            LinkedHashSet<String> modelIds = new LinkedHashSet<>();
            for (JsonNode model : models) {
                String modelId = model.path("id").asText();
                if (modelId.isBlank()) modelId = model.path("name").asText();
                if (!modelId.isBlank()) modelIds.add(modelId);
            }
            if (modelIds.isEmpty()) throw new RuntimeException("该服务商未返回可用模型");
            return List.copyOf(modelIds);
        } catch (IOException e) {
            throw new RuntimeException("解析模型列表失败", e);
        }
    }

    private String resolveApiKey(ModelConnectionCheckRequest request) {
        if (request.getType() == ModelProviderType.OLLAMA) return null;
        if (request.getAuthType() == AuthType.ENV) {
            if (!StringUtils.hasText(request.getEnvVarName())) {
                throw new IllegalArgumentException("请填写环境变量名");
            }
            String apiKey = System.getenv(request.getEnvVarName().trim());
            if (!StringUtils.hasText(apiKey)) {
                throw new IllegalArgumentException("环境变量未配置或为空");
            }
            return apiKey;
        }
        if (!StringUtils.hasText(request.getApiKey())) {
            throw new IllegalArgumentException("请填写 API Key");
        }
        return request.getApiKey().trim();
    }

    private CheckModelResult runConnectionCheck(ModelConfigWrapper configWrapper) {
        try {
            Model simpleModel = chatModelFactory.getSimpleModel(configWrapper);

            ReActAgent agent = ReActAgent.builder()
                    .name("CHECK_MODEL_AGENT")
                    .model(simpleModel)
                    .sysPrompt("For user inquiries, you must always respond with \"Connection test successful.\"")
                    .build();

            Msg msg = Msg.builder().textContent("hello").build();
            Msg response = agent.call(msg).block();
            if (response == null || response.getTextContent() == null) {
                return new CheckModelResult(false, "模型无响应");
            }
            return new CheckModelResult(true, "连接成功");
        } catch (Exception e) {
            return new CheckModelResult(false, e.getMessage());
        }
    }

    /**
     * 更新连接性检测结果到数据库
     */
    private void updateConnectivityResult(Long modelId, String status, String message) {
        ModelConfig entity = new ModelConfig();
        entity.setId(modelId);
        entity.setConnectivityStatus(status);
        entity.setConnectivityMessage(message);
        entity.setLastConnectivityCheck(LocalDateTime.now());
        modelConfigService.updateById(entity);
    }
}
