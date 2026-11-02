package com.hxh.apboa.common.dto;

import lombok.Data;

import java.util.List;

/** 管理端批量操作考试考生账号请求。 */
@Data
public class ExamCandidateBatchRequest {
    private List<Long> enrollmentIds;
    /** 批量启停时使用。 */
    private Boolean enabled;
}
