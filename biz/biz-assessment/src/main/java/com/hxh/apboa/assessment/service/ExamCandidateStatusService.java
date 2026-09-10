package com.hxh.apboa.assessment.service;

import com.hxh.apboa.common.vo.ExamCandidateStatusSnapshotVO;

/** 管理端考生考试状态服务。 */
public interface ExamCandidateStatusService {
    ExamCandidateStatusSnapshotVO getSnapshot(Long campaignId);
}
