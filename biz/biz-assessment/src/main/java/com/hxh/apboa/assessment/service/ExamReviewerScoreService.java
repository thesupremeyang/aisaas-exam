package com.hxh.apboa.assessment.service;

import com.hxh.apboa.assessment.dto.ExamScorePublishVO;
import com.hxh.apboa.assessment.dto.ExamScoreRowVO;
import com.hxh.apboa.assessment.dto.ExamSubmissionDetailVO;

import java.util.List;
import java.util.Map;

public interface ExamReviewerScoreService {
    /** 列出指定考试场次（跨租户）已提交作品的 AI 自评分数，可按学员姓名筛选。 */
    List<ExamScoreRowVO> listScores(Long campaignId, String nameFilter);

    /** 导出指定考试场次的全部报名学员成绩。 */
    byte[] exportScores(Long campaignId);

    /** 获取指定提交的完整复核详情。 */
    ExamSubmissionDetailVO getSubmissionDetail(Long submissionId);

    /** 获取指定考试报名下的学员总览，并展开每一道试题/智能体的成绩详情。 */
    ExamSubmissionDetailVO getEnrollmentDetail(Long enrollmentId);

    /** 管理员按题目分值保存最终得分与复核意见（覆盖式保存 assessment_review）。 */
    void saveFinalScore(Long submissionId, Integer finalScore, String comment, Long reviewerId);

    /**
     * 管理员保存最终得分，并标注来源。
     *
     * @param source 分数来源：MANUAL=人工复核，AI_ADOPT=采纳 AI 阅卷；为空按 MANUAL 处理
     */
    void saveFinalScore(Long submissionId, Integer finalScore, String comment, Long reviewerId, String source);

    /** 录入（或清空）单个考生的理论分，满分 100。 */
    void saveTheoryScore(Long enrollmentId, Integer theoryScore);

    /** 批量录入理论分，key 为报名记录 id，value 为理论分；返回实际更新条数。 */
    int batchSaveTheoryScore(Long campaignId, Map<Long, Integer> scores);

    /**
     * 发布本场成绩：把已复核的本题分汇总固化为报名记录的实操分，并开启查分。
     *
     * @param force 为 false 且仍有作品未复核时直接拒绝
     */
    ExamScorePublishVO publishCampaignScores(Long campaignId, Long operatorId, boolean force);
}
