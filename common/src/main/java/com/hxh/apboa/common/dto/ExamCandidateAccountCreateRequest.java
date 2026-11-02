package com.hxh.apboa.common.dto;

import lombok.Data;

/** 管理端创建考试考生账号请求。 */
@Data
public class ExamCandidateAccountCreateRequest {
    private String nickname;
    private String username;
    private String email;
    /** 前端 MD5 后的密码摘要。 */
    private String password;
    private String organization;
}
