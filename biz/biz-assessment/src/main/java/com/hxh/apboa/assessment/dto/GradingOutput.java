package com.hxh.apboa.assessment.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI 阅卷模型输出（配置量表 config-v1）。
 * 服务端会重算 baseScore 与 totalScore 做一致性校验，不盲信模型算术。
 */
public record GradingOutput(
        String plainComment,
        List<Item> items,
        BigDecimal baseScore,
        Integer checklistHits,
        List<String> checklistDetail,
        BigDecimal coefficient,
        String dominanceStatus,
        List<Channel> dominanceChannels,
        String capStatus,
        BigDecimal capRatio,
        BigDecimal deductionAmount,
        String deductionReason,
        BigDecimal totalScore,
        String confidence,
        String summary,
        List<String> integrityFlags
) {

    public record Item(String name, BigDecimal score, BigDecimal max, String evidence) {
    }

    public record Channel(String channel, String finding, String evidence) {
    }
}
