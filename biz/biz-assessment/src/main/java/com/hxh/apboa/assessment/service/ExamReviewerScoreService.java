package com.hxh.apboa.assessment.service;

import com.hxh.apboa.assessment.dto.ExamScoreRowVO;
import com.hxh.apboa.assessment.dto.ExamSubmissionDetailVO;

import java.util.List;

public interface ExamReviewerScoreService {
    /** 列出指定考试场次（跨租户）已提交作品的 AI 自评分数，可按学员姓名筛选。 */
    List<ExamScoreRowVO> listScores(Long campaignId, String nameFilter);

    /** 导出指定考试场次的全部报名学员成绩。 */
    byte[] exportScores(Long campaignId);

    /** 获取指定提交的完整复核详情。 */
    ExamSubmissionDetailVO getSubmissionDetail(Long submissionId);

    /** 获取指定考试报名下的学员总览，并展开每一道试题/智能体的成绩详情。 */
    ExamSubmissionDetailVO getEnrollmentDetail(Long enrollmentId);

    /** 管理员按题目分值保存最终得分与复核意见（覆盖式保存 assessment_review） */
    void saveFinalScore(Long submissionId, Integer finalScore, String comment, Long reviewerId);
}
