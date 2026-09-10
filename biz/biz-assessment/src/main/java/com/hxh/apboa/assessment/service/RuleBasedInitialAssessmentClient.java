package com.hxh.apboa.assessment.service;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Safe local fallback. It only evaluates the submitted form text. */
@Component
public class RuleBasedInitialAssessmentClient implements InitialAssessmentClient {
    @Override
    public InitialAssessmentResult assess(String submissionMaterial) {
        var scores = Map.of(
                "SCENE_VALUE", 17, "FUNCTION_COMPLETENESS", 17, "INNOVATION", 17,
                "TECHNICAL_DESCRIPTION", 17, "VERIFIABILITY", 17);
        var total = scores.values().stream().mapToInt(Integer::intValue).sum();
        AssessmentScoreValidator.validate(scores, total);
        return fallbackResult(scores, total, "建议通过，进入评委复核", "当前使用默认规则化初评。");
    }

    @Override
    public InitialAssessmentResult assess(String submissionMaterial, AssessmentRubric rubric) {
        if (rubric.maxScores().equals(AssessmentScoreValidator.RUBRIC_MAX_SCORES)) return assess(submissionMaterial);
        var scores = new LinkedHashMap<String, Integer>();
        rubric.maxScores().forEach((name, maximum) -> scores.put(name, (int) Math.floor(maximum * 0.85)));
        var total = scores.values().stream().mapToInt(Integer::intValue).sum();
        AssessmentScoreValidator.validate(scores, total, rubric);
        return fallbackResult(scores, total, "建议进入评委复核", "大模型服务不可用，当前使用规则化初评。");
    }

    private InitialAssessmentResult fallbackResult(Map<String, Integer> scores, int total, String conclusion, String risk) {
        return new InitialAssessmentResult(scores, total, "MEDIUM", conclusion,
                List.of(new AssessmentEvidence("综合材料", "依据考生填写的作品说明和材料元数据生成。")),
                List.of(risk, "未读取上传附件正文，需由评委结合原始材料复核。"),
                List.of("补充异常场景、运行环境和可复现步骤，可提升可验证性。"));
    }
}
