package com.hxh.apboa.common.vo;

import java.time.LocalDateTime;
import java.util.List;

/** Safe, read-only projection of an agent that may be selected as an examination work. */
public record ExamAgentSummaryVO(
        Long id,
        String name,
        String description,
        String agentType,
        String version,
        String tag,
        String modelName,
        Integer systemPromptLength,
        List<String> skillNames,
        List<String> mcpNames,
        List<String> knowledgeBaseNames,
        LocalDateTime updatedAt) {
}
