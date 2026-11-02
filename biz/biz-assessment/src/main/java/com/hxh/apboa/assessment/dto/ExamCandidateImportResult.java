package com.hxh.apboa.assessment.dto;

import java.util.List;

/** 批量导入汇总：解析行数、成功/失败数与逐行明细。 */
public record ExamCandidateImportResult(
        int totalRows,
        int successCount,
        int failureCount,
        List<ExamCandidateImportOutcome> outcomes) {
}
