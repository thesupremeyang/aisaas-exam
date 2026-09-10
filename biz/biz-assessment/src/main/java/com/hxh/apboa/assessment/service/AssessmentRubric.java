package com.hxh.apboa.assessment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/** 从考试批次 config_json 解析量表；配置缺失时使用 100 分默认量表。 */
public record AssessmentRubric(Map<String, Integer> maxScores, Map<String, Integer> levels, int maxWorks) {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, Integer> DEFAULT_SCORES = Map.of(
            "SCENE_VALUE", 20, "FUNCTION_COMPLETENESS", 20, "INNOVATION", 20,
            "TECHNICAL_DESCRIPTION", 20, "VERIFIABILITY", 20);
    private static final Map<String, Integer> DEFAULT_LEVELS = Map.of(
            "EXCELLENT", 90, "GOOD", 75, "PASS", 60);
    private static final int DEFAULT_MAX_WORKS = 3;
    private static final Map<String, String> DIMENSION_ALIASES = Map.of(
            "场景价值", "SCENE_VALUE", "功能完整度", "FUNCTION_COMPLETENESS", "创新性", "INNOVATION",
            "技术说明", "TECHNICAL_DESCRIPTION", "可验证性", "VERIFIABILITY");
    private static final Map<String, String> LEVEL_ALIASES = Map.of(
            "优秀", "EXCELLENT", "良好", "GOOD", "合格", "PASS");

    public static AssessmentRubric fromCampaignConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) return new AssessmentRubric(DEFAULT_SCORES, DEFAULT_LEVELS, DEFAULT_MAX_WORKS);
        try {
            JsonNode root = JSON.readTree(configJson);
            JsonNode rubric = root.has("rubric") ? root.path("rubric") : root.path("评分规则");
            var scores = readIntMap(rubric.has("dimensions") ? rubric.path("dimensions") : rubric.path("评分维度"), DIMENSION_ALIASES);
            var levels = readIntMap(rubric.has("levels") ? rubric.path("levels") : rubric.path("等级线"), LEVEL_ALIASES);
            int maxWorks = DEFAULT_MAX_WORKS;
            JsonNode mw = root.has("maxWorks") ? root.path("maxWorks")
                    : root.has("作品上限") ? root.path("作品上限")
                    : rubric.has("maxWorks") ? rubric.path("maxWorks")
                    : rubric.has("作品上限") ? rubric.path("作品上限") : null;
            if (mw != null && !mw.isMissingNode() && mw.isInt()) {
                maxWorks = mw.asInt(DEFAULT_MAX_WORKS);
            }
            return new AssessmentRubric(scores.isEmpty() ? DEFAULT_SCORES : scores,
                    levels.isEmpty() ? DEFAULT_LEVELS : levels, maxWorks);
        } catch (Exception ex) {
            throw new IllegalArgumentException("考试批次评分量表配置格式错误", ex);
        }
    }

    public int totalScore() {
        return maxScores.values().stream().mapToInt(Integer::intValue).sum();
    }

    public String levelOf(int score) {
        if (score < 0 || score > totalScore()) throw new IllegalArgumentException("最终得分超出量表范围");
        if (score >= levels.getOrDefault("EXCELLENT", Integer.MAX_VALUE)) return "EXCELLENT";
        if (score >= levels.getOrDefault("GOOD", Integer.MAX_VALUE)) return "GOOD";
        if (score >= levels.getOrDefault("PASS", Integer.MAX_VALUE)) return "PASS";
        return "FAIL";
    }

    private static Map<String, Integer> readIntMap(JsonNode node, Map<String, String> aliases) {
        var values = new LinkedHashMap<String, Integer>();
        node.fields().forEachRemaining(field ->
                values.put(aliases.getOrDefault(field.getKey(), field.getKey()), field.getValue().asInt()));
        return values;
    }
}
