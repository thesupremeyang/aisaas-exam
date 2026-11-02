package com.hxh.apboa.assessment.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 加载纯配置版评分量表（config-rubric.md），按题号切分出公共规则 / 单题分项表 / 输出格式三段，
 * 供阅卷智能体按当次评阅的题目拼装 system prompt，避免全量表注入造成混乱。
 */
@Component
public class ExamGradingRubricStore {

    private static final String RESOURCE = "/grading/config-rubric.md";
    public static final String VERSION = "config-v1";

    /** 四题分项满分之和（与本场考试题目设置分值 20/20/30/30 一致）；实际评分上限优先取 exam_question.score。 */
    private static final java.math.BigDecimal[] CONFIG_MAX = {
            new java.math.BigDecimal("20"), new java.math.BigDecimal("20"),
            new java.math.BigDecimal("30"), new java.math.BigDecimal("30")
    };

    private final String commonPart;
    private final Map<Integer, String> questionParts = new HashMap<>();
    private final String outputPart;

    public ExamGradingRubricStore() {
        String text = readResource();
        int q1 = indexOfHeading(text, "## 第 1 题");
        int level = indexOfHeading(text, "## 结果等级参考");
        int output = indexOfHeading(text, "## 输出 JSON 格式");
        if (q1 < 0 || output < 0) {
            throw new IllegalStateException("评分量表资源损坏：缺少题目分节或输出格式分节");
        }
        this.commonPart = text.substring(0, q1).trim();
        for (int n = 1; n <= 4; n++) {
            int start = indexOfHeading(text, "## 第 " + n + " 题");
            if (start < 0) {
                throw new IllegalStateException("评分量表资源损坏：缺少第 " + n + " 题分节");
            }
            int end = text.length();
            for (int m = n + 1; m <= 4; m++) {
                int next = indexOfHeading(text, "## 第 " + m + " 题");
                if (next > start) {
                    end = Math.min(end, next);
                }
            }
            if (level > start) {
                end = Math.min(end, level);
            }
            if (output > start) {
                end = Math.min(end, output);
            }
            questionParts.put(n, text.substring(start, end).trim());
        }
        StringBuilder tail = new StringBuilder();
        if (level > 0) {
            tail.append(text.substring(level, output).trim()).append("\n\n");
        }
        tail.append(text.substring(output).trim());
        this.outputPart = tail.toString();
    }

    public String commonPart() {
        return commonPart;
    }

    /** sortOrder 1-4；越界或缺失抛出异常，由任务层转为 FAILED。 */
    public String questionPart(int questionNo) {
        String part = questionParts.get(questionNo);
        if (part == null) {
            throw new IllegalArgumentException("量表不包含第 " + questionNo + " 题分节（本量表仅适用本场四题考试）");
        }
        return part;
    }

    public String outputPart() {
        return outputPart;
    }

    public String version() {
        return VERSION;
    }

    /** 指定题号的配置满分（questionNo 1-4）。 */
    public java.math.BigDecimal configMax(int questionNo) {
        if (questionNo < 1 || questionNo > CONFIG_MAX.length) {
            throw new IllegalArgumentException("题号越界：" + questionNo);
        }
        return CONFIG_MAX[questionNo - 1];
    }

    private int indexOfHeading(String text, String heading) {
        return text.indexOf(heading);
    }

    private String readResource() {
        try (InputStream in = ExamGradingRubricStore.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("评分量表资源缺失：" + RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("评分量表资源读取失败：" + RESOURCE, ex);
        }
    }
}
