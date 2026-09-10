package com.hxh.apboa.assessment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hxh.apboa.common.entity.ExamCampaign;

/**
 * 考试批次查分模式配置。
 *
 * <p>沿用考试批次已有的 config_json，避免为一次考试状态增加新的表字段。</p>
 */
public final class ExamCampaignScoreMode {
    private static final String SCORE_QUERY_ENABLED = "scoreQueryEnabled";
    private static final ObjectMapper JSON = new ObjectMapper();

    private ExamCampaignScoreMode() {
    }

    public static boolean isEnabled(ExamCampaign campaign) {
        return campaign != null && isEnabled(campaign.getConfigJson());
    }

    public static boolean isEnabled(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return false;
        }
        try {
            return JSON.readTree(configJson).path(SCORE_QUERY_ENABLED).asBoolean(false);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void decorate(ExamCampaign campaign) {
        if (campaign != null) {
            campaign.setScoreQueryEnabled(isEnabled(campaign.getConfigJson()));
        }
    }

    public static String withEnabled(String configJson, boolean enabled) {
        try {
            JsonNode parsed = configJson == null || configJson.isBlank()
                    ? JSON.createObjectNode()
                    : JSON.readTree(configJson);
            if (!parsed.isObject()) {
                throw new IllegalArgumentException("考试批次配置必须是 JSON 对象");
            }
            ObjectNode root = (ObjectNode) parsed;
            root.put(SCORE_QUERY_ENABLED, enabled);
            return JSON.writeValueAsString(root);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("考试批次配置格式错误", exception);
        }
    }
}
