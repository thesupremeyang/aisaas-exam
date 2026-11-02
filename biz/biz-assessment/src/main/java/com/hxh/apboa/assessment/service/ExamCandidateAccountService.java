package com.hxh.apboa.assessment.service;

import com.hxh.apboa.assessment.dto.ExamCandidateImportResult;
import com.hxh.apboa.common.dto.ExamCandidateAccountCreateRequest;
import com.hxh.apboa.common.dto.ExamCandidateAccountUpdateRequest;

import java.util.List;

/** 平台管理员维护每场考试的考生账号。 */
public interface ExamCandidateAccountService {
    Long create(Long campaignId, ExamCandidateAccountCreateRequest request);

    boolean update(Long campaignId, Long enrollmentId, ExamCandidateAccountUpdateRequest request);

    boolean changePassword(Long campaignId, Long enrollmentId, String password);

    boolean resetPassword(Long campaignId, Long enrollmentId);

    boolean updateEnabled(Long campaignId, Long enrollmentId, Boolean enabled);

    boolean remove(Long campaignId, Long enrollmentId);

    /** 批量重置密码，返回成功处理数量。 */
    int batchResetPassword(Long campaignId, List<Long> enrollmentIds);

    /** 批量启停，返回成功处理数量。 */
    int batchUpdateEnabled(Long campaignId, List<Long> enrollmentIds, Boolean enabled);

    /** 批量移出考试，返回成功处理数量。 */
    int batchRemove(Long campaignId, List<Long> enrollmentIds);

    /** 解析 Excel 名单并逐行创建考生（建号 + 独立考生空间 + 报名），返回逐行结果。 */
    ExamCandidateImportResult batchImport(Long campaignId, byte[] fileBytes, String fileName);

    /** 生成考生批量导入 Excel 模板。 */
    byte[] buildImportTemplate();
}
