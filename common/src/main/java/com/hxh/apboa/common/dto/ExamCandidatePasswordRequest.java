package com.hxh.apboa.common.dto;

import lombok.Data;

/** 管理端修改考试考生密码请求。 */
@Data
public class ExamCandidatePasswordRequest {
    /** 前端 MD5 后的密码摘要。 */
    private String password;
}
