package com.hxh.apboa.common.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/** 管理端考试状态快照。 */
@Getter
@Setter
public class ExamCandidateStatusSnapshotVO {
    private Long campaignId;
    private String campaignName;
    private String examState;
    private LocalDateTime examStartAt;
    private LocalDateTime examEndAt;
    private LocalDateTime serverNow;
    private boolean loginStatusAvailable = true;
    private int totalCandidates;
    private int loggedInCount;
    private int enteredCount;
    private int submittedCount;
    private List<ExamCandidateStatusVO> candidates;
}
