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

        /** 页面展示的分值：按本题分值折算后的成绩。 */
        private Integer aiScore;
        private Integer aiRawScore;
        private Integer rawScoreLimit;
        private Map<String, Integer> dimensionScores = Map.of();
        private String confidence;
        private String modelName;
        private LocalDateTime aiScoredAt;
        private String conclusion;
        private List<SubmissionDimEvidenceVO> evidence = List.of();
        private List<String> risks = List.of();
        private List<String> suggestions = List.of();

        /** 新版复核记录直接保存本题最终分；历史量表记录兼容提供折算后的最终分。 */
        private Integer finalScore;
        /** 仅用于兼容展示历史复核记录中的评分量表原始分。 */
        private Integer finalRawScore;
        private Map<String, Integer> reviewDimensionScores = Map.of();
        private String reviewComment;
        private boolean reviewed;
    }
}
