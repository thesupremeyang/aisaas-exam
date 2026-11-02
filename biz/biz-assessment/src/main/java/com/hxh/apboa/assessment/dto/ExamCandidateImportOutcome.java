package com.hxh.apboa.assessment.dto;

/** 单行导入结果。失败时 errorMessage 说明原因（账号已存在、姓名超长等）。 */
public record ExamCandidateImportOutcome(
        int rowNumber,
        String name,
        String username,
        boolean success,
        String errorMessage) {
}
