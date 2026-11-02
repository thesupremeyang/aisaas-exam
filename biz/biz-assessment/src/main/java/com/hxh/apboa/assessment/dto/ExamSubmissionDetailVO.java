package com.hxh.apboa.assessment.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class ExamSubmissionDetailVO {
    /** 聚合详情所属的考试报名。 */
    private Long enrollmentId;
    private Long submissionId;
    private String candidateName;
    private String identityNumber;
    private String loginName;
    private String organization;
    private String tenantName;
    /** 考生租户 ID，用于管理员进入考生配置空间 */
    private Long tenantId;
    private String phone;
    private String email;
    private String workName;
    private String workType;
    private String workTypeLabel;
    private String submissionStatus;
    private LocalDateTime submittedAt;
    private Long agentId;
    private String agentName;
    private String scene;
    private String functionDescription;
    private String innovationDescription;
    private String promotionDescription;
    private String dataDescription;
    private String runtimeEnvironment;
    private String teamDescription;
    private String technicalDescription;
    private boolean hasSkill;
    private boolean hasMcp;
    private boolean hasKnowledge;
    private boolean hasDemo;
    private String demoUrl;
    private String materialDescription;
    private List<SubmissionFileVO> screenshotFiles = List.of();
    private List<SubmissionFileVO> videoFiles = List.of();
    private List<SubmissionFileVO> packageFiles = List.of();
    private List<SubmissionFileVO> supplementaryFiles = List.of();
    private Map<String, Integer> dimensionScores = Map.of();
    private Integer aiTotalScore;
    private Integer totalLimit;
    private String confidence;
    private String modelName;
    private LocalDateTime aiScoredAt;
    private String conclusion;
    private List<SubmissionDimEvidenceVO> evidence = List.of();
    private List<String> risks = List.of();
    private List<String> suggestions = List.of();
    private Integer finalScore;
    private String reviewComment;
    private boolean reviewed;
    /** 理论成绩（满分 100），由管理员录入；总成绩 = 理论×20% + 实操×80%。 */
    private Integer theoryScore;
    /** 实操成绩（各题复核得分之和），发布成绩时固化。 */
    private Integer practicalScore;
    private Integer questionCount;
    private Integer submittedQuestionCount;
    private Integer aiScoredQuestionCount;
    private Integer reviewedQuestionCount;
    private Integer demoCount;
    private List<QuestionScoreVO> questions = List.of();

    @Getter
    @Setter
    public static class SubmissionFileVO {
        private String url;
        private String name;
        private Long size;
    }

    @Getter
    @Setter
    public static class SubmissionDimEvidenceVO {
        private String dimension;
        private String content;
    }

    /** 学员某一道试题（一个智能体作品）的成绩与提交详情。 */
    @Getter
    @Setter
    public static class QuestionScoreVO {
        private Integer questionNo;
        private Long questionId;
        private String questionTitle;
        private String questionScenario;
        private Integer questionScore;
        private Long submissionId;
        private Long agentId;
        private String agentName;
        private String workName;
        private String workType;
        private String workTypeLabel;
        private String submissionStatus;
        private LocalDateTime submittedAt;
        private String scene;
        private String functionDescription;
        private String innovationDescription;
        private String promotionDescription;
        private String dataDescription;
        private String runtimeEnvironment;
        private String teamDescription;
        private String technicalDescription;
        private boolean hasSkill;
        private boolean hasMcp;
        private boolean hasKnowledge;
        private boolean hasDemo;
        private String demoUrl;
        private String materialDescription;
        private List<SubmissionFileVO> screenshotFiles = List.of();
        private List<SubmissionFileVO> videoFiles = List.of();
        private List<SubmissionFileVO> packageFiles = List.of();
        private List<SubmissionFileVO> supplementaryFiles = List.of();

        /** 页面展示的分值：按本题分值折算后的 AI 阅卷成绩。 */
        private Integer aiScore;
        /** AI 阅卷原始分（配置量表） */
        private Integer aiRawScore;
        /** AI 阅卷原始分满分（该题配置可评分项满分） */
        private Integer aiMaxScore;
        /** 仅用于兼容展示历史复核记录中的评分量表满分。 */
        private Integer rawScoreLimit;
        private Map<String, Integer> dimensionScores = Map.of();
        private String confidence;
        private String modelName;
        private LocalDateTime aiScoredAt;
        private String conclusion;
        private List<SubmissionDimEvidenceVO> evidence = List.of();
        private List<String> risks = List.of();
        private List<String> suggestions = List.of();

        /** AI 阅卷报告：分项评分（含满分与评分依据），来自 reportJson.items。 */
        private List<GradingDimItemVO> dimensionItems = List.of();
        /** AI 阅卷报告：面向考生的通俗评语。 */
        private String plainComment;
        /** AI 阅卷报告：设计清单命中条数。 */
        private Integer checklistHits;
        /** AI 阅卷报告：设计清单逐条命中说明。 */
        private List<String> checklistDetail = List.of();
        /** AI 阅卷报告：完整性 / 真实性标记。 */
        private List<String> integrityFlags = List.of();
        /** AI 阅卷报告：主导权（疑似外包）判定状态。 */
        private String dominanceStatus;
        /** AI 阅卷报告：主导权判定通道明细（A~E）。 */
        private List<GradingChannelVO> dominanceChannels = List.of();
        /** AI 阅卷报告：主导权封顶比例与封顶原因。 */
        private java.math.BigDecimal capRatio;
        private String capStatus;
        /** AI 阅卷报告：额外扣分与扣分原因。 */
        private java.math.BigDecimal deductionAmount;
        private String deductionReason;

        /** 新版复核记录直接保存本题最终分；历史量表记录兼容提供折算后的最终分。 */
        private Integer finalScore;
        /** 仅用于兼容展示历史复核记录中的评分量表原始分。 */
        private Integer finalRawScore;
        private Map<String, Integer> reviewDimensionScores = Map.of();
        private String reviewComment;
        private boolean reviewed;
    }

    /** AI 阅卷报告的单个评分项：维度名、得分、满分、评分依据。 */
    @Getter
    @Setter
    public static class GradingDimItemVO {
        private String dimension;
        private java.math.BigDecimal score;
        private java.math.BigDecimal max;
        private String evidence;
    }

    /** AI 阅卷报告的主导权判定通道（A~E）：通道、命中结论、依据。 */
    @Getter
    @Setter
    public static class GradingChannelVO {
        private String channel;
        private String finding;
        private String evidence;
    }
}
