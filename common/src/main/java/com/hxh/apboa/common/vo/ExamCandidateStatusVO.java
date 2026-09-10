package com.hxh.apboa.common.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 管理端考生考试状态。 */
@Getter
@Setter
public class ExamCandidateStatusVO {
    private Long enrollmentId;
    private Long accountId;
    private String candidateName;
    private String username;
    private String organization;
    private boolean loggedIn;
    private boolean entered;
    private LocalDateTime enteredAt;
    private String submissionStatus;
    private String examState;
    private Long elapsedSeconds;
    private Long remainingSeconds;
}
