package com.hxh.apboa.common.dto;

import lombok.Data;

/** 管理端修改考试考生账号资料请求。 */
@Data
public class ExamCandidateAccountUpdateRequest {
    private String nickname;
    private String username;
    private String email;
    private String organization;
}
