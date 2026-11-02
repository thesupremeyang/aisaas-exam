package com.hxh.apboa.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.consts.TableConst;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 阅卷结果（配置量表 config-v1）。
 * totalScore = baseScore × coefficient（含主导权封顶），configMaxScore 为该题配置可评分项满分，
 * normalizedScore = totalScore / configMaxScore × 100（百分制参考分）。
 */
@Getter
@Setter
@TableName(TableConst.EXAM_GRADING_RESULT)
public class ExamGradingResult extends BaseTenantEntity {
    private Long submissionId;
    private Long taskId;
    private Long agentId;
    private Long questionId;
    /** 分项锚点基础分 */
    private BigDecimal baseScore;
    /** 设计优越性系数（含主导权封顶后实际生效值） */
    private BigDecimal coefficient;
    /** 主导权封顶上限，未封顶为 null */
    private BigDecimal capRatio;
    /** 封顶标注：整体外包设计 / 疑似外包设计，未封顶为 null */
    private String capStatus;
    /** 确认外包设计 / 疑似外包设计 / 中度嫌疑 / 轻度残留 / 工具型使用 */
    private String dominanceStatus;
    /** 本题最终得分（配置量表） */
    private BigDecimal totalScore;
    /** 该题配置可评分项满分 */
    private BigDecimal configMaxScore;
    /** 折算百分制参考分 */
    private BigDecimal normalizedScore;
    private String confidence;
    private String rubricVersion;
    private String promptVersion;
    private String modelName;
    /** 分项明细/证据/通道判定/扣分原因/异常标记 */
    private String reportJson;
}
