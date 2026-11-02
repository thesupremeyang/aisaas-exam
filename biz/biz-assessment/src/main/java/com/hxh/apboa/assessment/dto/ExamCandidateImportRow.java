package com.hxh.apboa.assessment.dto;

/** Excel 导入解析出的单个考生行。rowNumber 为 Excel 实际行号（从 1 开始）。 */
public record ExamCandidateImportRow(
        int rowNumber,
        String name,
        String username,
        String password,
        String organization) {
}
