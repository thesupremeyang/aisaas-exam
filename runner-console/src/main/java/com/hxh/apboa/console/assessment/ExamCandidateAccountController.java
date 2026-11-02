package com.hxh.apboa.console.assessment;

import com.hxh.apboa.assessment.dto.ExamCandidateImportResult;
import com.hxh.apboa.assessment.service.ExamCandidateAccountService;
import com.hxh.apboa.common.dto.ExamCandidateAccountCreateRequest;
import com.hxh.apboa.common.dto.ExamCandidateAccountUpdateRequest;
import com.hxh.apboa.common.dto.ExamCandidateBatchRequest;
import com.hxh.apboa.common.dto.ExamCandidatePasswordRequest;
import com.hxh.apboa.common.exception.NotAuthException;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.common.util.UserUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/** 平台 admin 专用的考试考生账号管理接口。 */
@RestController
@RequestMapping("/exam/admin/campaigns/{campaignId}/candidates")
@RequiredArgsConstructor
public class ExamCandidateAccountController {
    private final ExamCandidateAccountService candidateAccountService;

    @PostMapping
    public R<Long> create(@PathVariable Long campaignId,
                          @RequestBody ExamCandidateAccountCreateRequest request) {
        requirePlatformAdmin();
        return R.data(candidateAccountService.create(campaignId, request));
    }

    /** 下载考生批量导入模板。 */
    @GetMapping("/import-template")
    public ResponseEntity<byte[]> importTemplate() {
        requirePlatformAdmin();
        byte[] content = candidateAccountService.buildImportTemplate();
        return excelAttachment("考生批量导入模板.xlsx", content);
    }

    /** 上传 Excel 名单批量创建考生，逐行返回导入结果。 */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<ExamCandidateImportResult> importCandidates(@PathVariable Long campaignId,
                                                         @RequestParam("file") MultipartFile file) {
        requirePlatformAdmin();
        if (file == null) {
            return R.fail("请上传考生名单 Excel 文件");
        }
        try {
            return R.data(candidateAccountService.batchImport(
                    campaignId, file.getBytes(), file.getOriginalFilename()));
        } catch (java.io.IOException exception) {
            return R.fail("读取上传文件失败，请重试");
        }
    }

    private ResponseEntity<byte[]> excelAttachment(String filename, byte[] content) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(content.length);
        return new ResponseEntity<>(content, headers, HttpStatus.OK);
    }

    @PutMapping("/{enrollmentId}")
    public R<Boolean> update(@PathVariable Long campaignId,
                             @PathVariable Long enrollmentId,
                             @RequestBody ExamCandidateAccountUpdateRequest request) {
        requirePlatformAdmin();
        return R.data(candidateAccountService.update(campaignId, enrollmentId, request));
    }

    @PutMapping("/{enrollmentId}/password")
    public R<Boolean> changePassword(@PathVariable Long campaignId,
                                     @PathVariable Long enrollmentId,
                                     @RequestBody ExamCandidatePasswordRequest request) {
        requirePlatformAdmin();
        return R.data(candidateAccountService.changePassword(
                campaignId, enrollmentId, request == null ? null : request.getPassword()));
    }

    @PostMapping("/{enrollmentId}/reset-password")
    public R<Boolean> resetPassword(@PathVariable Long campaignId,
                                    @PathVariable Long enrollmentId) {
        requirePlatformAdmin();
        return R.data(candidateAccountService.resetPassword(campaignId, enrollmentId));
    }

    @PatchMapping("/{enrollmentId}/enabled")
    public R<Boolean> updateEnabled(@PathVariable Long campaignId,
                                    @PathVariable Long enrollmentId,
                                    @RequestParam("enabled") Boolean enabled) {
        requirePlatformAdmin();
        return R.data(candidateAccountService.updateEnabled(campaignId, enrollmentId, enabled));
    }

    @DeleteMapping("/{enrollmentId}")
    public R<Boolean> remove(@PathVariable Long campaignId,
                             @PathVariable Long enrollmentId) {
        requirePlatformAdmin();
        return R.data(candidateAccountService.remove(campaignId, enrollmentId));
    }

    @PostMapping("/batch-reset-password")
    public R<Integer> batchResetPassword(@PathVariable Long campaignId,
                                         @RequestBody ExamCandidateBatchRequest request) {
        requirePlatformAdmin();
        return R.data(candidateAccountService.batchResetPassword(
                campaignId, request == null ? null : request.getEnrollmentIds()));
    }

    @PatchMapping("/batch-enabled")
    public R<Integer> batchUpdateEnabled(@PathVariable Long campaignId,
                                         @RequestBody ExamCandidateBatchRequest request) {
        requirePlatformAdmin();
        return R.data(candidateAccountService.batchUpdateEnabled(
                campaignId,
                request == null ? null : request.getEnrollmentIds(),
                request == null ? null : request.getEnabled()));
    }

    @PostMapping("/batch-remove")
    public R<Integer> batchRemove(@PathVariable Long campaignId,
                                  @RequestBody ExamCandidateBatchRequest request) {
        requirePlatformAdmin();
        return R.data(candidateAccountService.batchRemove(
                campaignId, request == null ? null : request.getEnrollmentIds()));
    }

    private void requirePlatformAdmin() {
        if (!UserUtils.isPlatformAdmin()) {
            throw new NotAuthException("仅平台 admin 可管理考生账号");
        }
    }
}
