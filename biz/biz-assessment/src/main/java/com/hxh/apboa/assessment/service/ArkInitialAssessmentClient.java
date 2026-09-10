package com.hxh.apboa.assessment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 火山方舟 Ark（OpenAI 兼容 /chat/completions）初评实现。
 * 直接用租户配置的大模型对提交材料打分，替代原本依赖 AI Runtime 的 HTTP 网关。
 */
@Component
public class ArkInitialAssessmentClient implements InitialAssessmentClient {

    private final ObjectMapper jsonMapper;
    private final HttpClient httpClient;

    @Value("${assessment.ark.endpoint:}")
    private String endpoint;

    @Value("${assessment.ark.api-key:}")
    private String apiKey;

    @Value("${assessment.ark.model:deepseek-v4-flash}")
    private String model;

    @Value("${assessment.ark.system-prompt:"
            + "你是严格的考试评审专家。请依据下方评分量表与考生提交材料进行真实评分。\n"
            + "评分铁律：\n"
            + "1. 必须先判断提交材料是否有效、实质性：若内容为空、长度过短、或仅为"
            + "无意义的乱填字符（如重复的 1/2/3/4/5、纯数字、与题目无关的占位文本、"
            + "复制粘贴的无关内容等），一律认定为【无效作答】，各维度必须给最低分，"
            + "最终总分需低于及格线（PASS 等级线），并在 conclusion 中明确说明“内容无效/乱填，判定不及格”。\n"
            + "2. 只有材料真实、具体、切题时，才按质量给分，严禁平均主义或人情分。\n"
            + "3. 仅输出 JSON，包含 dimensionScores（各维度整数分）、totalScore（整数总分）、"
            + "confidence、conclusion、evidence。\n"
            + "4. 必须严格依据给定评分量表维度与满分上限打分，totalScore 必须等于各维度分之和。}")
    private String arkSystem;

    @Value("${assessment.ark.timeout:90s}")
    private Duration timeout;

    public ArkInitialAssessmentClient(ObjectMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    }

    @Override
    public InitialAssessmentResult assess(String submissionMaterial) {
        return assess(submissionMaterial, AssessmentRubric.fromCampaignConfig(null));
    }

    @Override
    public InitialAssessmentResult assess(String submissionMaterial, AssessmentRubric rubric) {
        String requestJson = buildRequest(submissionMaterial, rubric);
        String responseJson = invoke(requestJson);
        return parseAndValidate(responseJson, rubric);
    }

    private String buildRequest(String submissionMaterial, AssessmentRubric rubric) {
        try {
            var assessmentContext = new AssessmentPromptBuilder(jsonMapper).build(submissionMaterial, rubric);
            var request = new LinkedHashMap<String, Object>();
            request.put("model", model);
            request.put("messages", new Object[]{
                    Map.of("role", "system", "content", arkSystem),
                    Map.of("role", "user", "content", assessmentContext)
            });
            request.put("response_format", Map.of("type", "json_object"));
            return jsonMapper.writeValueAsString(request);
        } catch (Exception ex) {
            throw new RuntimeAssessmentException(RuntimeAssessmentException.Kind.INVALID_RESPONSE,
                    "Unable to serialize Ark request", ex);
        }
    }

    private String invoke(String requestJson) {
        try {
            var builder = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson));
            var response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeAssessmentException(RuntimeAssessmentException.Kind.HTTP_STATUS,
                        "Ark returned HTTP " + response.statusCode());
            }
            return response.body();
        } catch (java.net.http.HttpTimeoutException ex) {
            throw new RuntimeAssessmentException(RuntimeAssessmentException.Kind.TIMEOUT, "Ark request timed out", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeAssessmentException(RuntimeAssessmentException.Kind.CONNECTION, "Ark request interrupted", ex);
        } catch (java.io.IOException ex) {
            throw new RuntimeAssessmentException(RuntimeAssessmentException.Kind.CONNECTION, "Ark connection failed", ex);
        }
    }

    private InitialAssessmentResult parseAndValidate(String responseJson, AssessmentRubric rubric) {
        try {
            JsonNode root = jsonMapper.readTree(responseJson);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                String content = choices.get(0).path("message").path("content").asText("");
                if (!content.isBlank()) {
                    responseJson = content;
                }
            }
            JsonNode data = jsonMapper.readTree(responseJson);
            var scores = new LinkedHashMap<String, Integer>();
            data.path("dimensionScores").fields().forEachRemaining(
                    e -> scores.put(e.getKey(), e.getValue().intValue()));
            int total = requiredInt(data, "totalScore");
            String confidence = requiredText(data, "confidence");
            String conclusion = requiredText(data, "conclusion");
            var evidence = readEvidence(data.path("evidence"));
            var risks = readTextList(data.path("risks"));
            var suggestions = readTextList(data.path("suggestions"));
            AssessmentScoreValidator.validate(scores, total, rubric);
            return new InitialAssessmentResult(scores, total, confidence, conclusion, evidence, risks, suggestions);
        } catch (RuntimeAssessmentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeAssessmentException(RuntimeAssessmentException.Kind.INVALID_RESPONSE,
                    "Ark returned invalid assessment JSON", ex);
        }
    }

    private int requiredInt(JsonNode root, String field) {
        if (!root.has(field) || !root.path(field).canConvertToInt()) {
            throw new RuntimeAssessmentException(RuntimeAssessmentException.Kind.INVALID_RESPONSE,
                    "Missing integer field: " + field, null);
        }
        return root.path(field).intValue();
    }

    private String requiredText(JsonNode root, String field) {
        var value = root.path(field).asText("").trim();
        if (value.isEmpty()) {
            throw new RuntimeAssessmentException(RuntimeAssessmentException.Kind.INVALID_RESPONSE,
                    "Missing text field: " + field, null);
        }
        return value;
    }

    private List<AssessmentEvidence> readEvidence(JsonNode value) {
        var result = new ArrayList<AssessmentEvidence>();
        if (value.isTextual() && !value.asText().isBlank()) {
            result.add(new AssessmentEvidence("综合材料", value.asText()));
            return result;
        }
        if (value.isArray()) {
            value.forEach(item -> {
                String content = item.path("content").asText("").trim();
                if (!content.isEmpty()) result.add(new AssessmentEvidence(item.path("dimension").asText("综合材料"), content));
            });
        }
        return result;
    }

    private List<String> readTextList(JsonNode value) {
        var result = new ArrayList<String>();
        if (value.isTextual() && !value.asText().isBlank()) result.add(value.asText());
        if (value.isArray()) value.forEach(item -> {
            String text = item.asText("").trim();
            if (!text.isEmpty()) result.add(text);
        });
        return result;
    }
}
