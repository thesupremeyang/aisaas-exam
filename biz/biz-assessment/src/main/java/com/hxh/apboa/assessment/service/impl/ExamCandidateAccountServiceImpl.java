package com.hxh.apboa.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hxh.apboa.account.mapper.AccountMapper;
import com.hxh.apboa.account.service.AccountService;
import com.hxh.apboa.assessment.dto.ExamCandidateImportOutcome;
import com.hxh.apboa.assessment.dto.ExamCandidateImportResult;
import com.hxh.apboa.assessment.dto.ExamCandidateImportRow;
import com.hxh.apboa.assessment.mapper.ExamCampaignMapper;
import com.hxh.apboa.assessment.mapper.ExamEnrollmentMapper;
import com.hxh.apboa.assessment.service.ExamCandidateAccountService;
import com.hxh.apboa.common.dto.ExamCandidateAccountCreateRequest;
import com.hxh.apboa.common.dto.ExamCandidateAccountUpdateRequest;
import com.hxh.apboa.common.dto.RegisterRequest;
import com.hxh.apboa.common.entity.Account;
import com.hxh.apboa.common.entity.ExamCampaign;
import com.hxh.apboa.common.entity.ExamEnrollment;
import com.hxh.apboa.common.exception.BusinessException;
import com.hxh.apboa.common.util.CryptoUtils;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.common.util.UserUtils;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ExamCandidateAccountServiceImpl implements ExamCandidateAccountService {
    public static final String DEFAULT_PASSWORD = "12345678";

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{4,20}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern MD5_PATTERN = Pattern.compile("^[A-Fa-f0-9]{32}$");

    /** 批量导入单次上限与文件大小上限：每行都会初始化一个考生独立租户，量级必须收敛。 */
    private static final int MAX_IMPORT_ROWS = 200;
    private static final int MAX_IMPORT_BYTES = 2 * 1024 * 1024;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String USERNAME_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";

    private final ExamCampaignMapper campaignMapper;
    private final ExamEnrollmentMapper enrollmentMapper;
    private final AccountMapper accountMapper;
    private final AccountService accountService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(Long campaignId, ExamCandidateAccountCreateRequest request) {
        return withoutTenantScope(() -> createInternal(campaignId, request));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(Long campaignId, Long enrollmentId, ExamCandidateAccountUpdateRequest request) {
        return withoutTenantScope(() -> updateInternal(campaignId, enrollmentId, request));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changePassword(Long campaignId, Long enrollmentId, String password) {
        return withoutTenantScope(() -> {
            validatePasswordDigest(password);
            ExamEnrollment enrollment = findEnrollment(campaignId, enrollmentId);
            ensureNotPlatformAdmin(findAccount(enrollment.getAccountId()));
            return accountService.adminChangePassword(enrollment.getAccountId(), password);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean resetPassword(Long campaignId, Long enrollmentId) {
        return withoutTenantScope(() -> {
            ExamEnrollment enrollment = findEnrollment(campaignId, enrollmentId);
            ensureNotPlatformAdmin(findAccount(enrollment.getAccountId()));
            return accountService.adminChangePassword(
                    enrollment.getAccountId(), CryptoUtils.md5(DEFAULT_PASSWORD));
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateEnabled(Long campaignId, Long enrollmentId, Boolean enabled) {
        return withoutTenantScope(() -> {
            if (enabled == null) {
                throw new BusinessException("请指定账号是否启用");
            }
            ExamEnrollment enrollment = findEnrollment(campaignId, enrollmentId);
            Account account = findAccount(enrollment.getAccountId());
            ensureNotPlatformAdmin(account);
            account.setEnabled(enabled);
            return accountMapper.updateById(account) == 1;
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long campaignId, Long enrollmentId) {
        return withoutTenantScope(() -> {
            ExamEnrollment enrollment = findEnrollment(campaignId, enrollmentId);
            Account account = findAccount(enrollment.getAccountId());
            ensureNotPlatformAdmin(account);

            enrollment.setEnabled(false);
            enrollment.setStatus("REMOVED");
            if (enrollmentMapper.updateById(enrollment) != 1) {
                return false;
            }

            long activeEnrollments = enrollmentMapper.selectCount(
                    new LambdaQueryWrapper<ExamEnrollment>()
                            .eq(ExamEnrollment::getAccountId, enrollment.getAccountId())
                            .eq(ExamEnrollment::getEnabled, true)
                            .eq(ExamEnrollment::getStatus, "REGISTERED")
                            .apply("del_flag = 0"));
            if (activeEnrollments == 0) {
                account.setEnabled(false);
                accountMapper.updateById(account);
            }
            return true;
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchResetPassword(Long campaignId, List<Long> enrollmentIds) {
        return runBatch(enrollmentIds, id -> resetPassword(campaignId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchUpdateEnabled(Long campaignId, List<Long> enrollmentIds, Boolean enabled) {
        return runBatch(enrollmentIds, id -> updateEnabled(campaignId, id, enabled));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchRemove(Long campaignId, List<Long> enrollmentIds) {
        return runBatch(enrollmentIds, id -> remove(campaignId, id));
    }

    private int runBatch(List<Long> enrollmentIds, Function<Long, Boolean> action) {
        if (enrollmentIds == null || enrollmentIds.isEmpty()) {
            throw new BusinessException("请至少选择一名考生");
        }
        int success = 0;
        for (Long enrollmentId : enrollmentIds.stream().filter(Objects::nonNull).distinct().toList()) {
            if (Boolean.TRUE.equals(action.apply(enrollmentId))) {
                success++;
            }
        }
        return success;
    }

    @Override
    public ExamCandidateImportResult batchImport(Long campaignId, byte[] fileBytes, String fileName) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new BusinessException("请上传考生名单 Excel 文件");
        }
        if (fileBytes.length > MAX_IMPORT_BYTES) {
            throw new BusinessException("Excel 文件不能超过 2MB");
        }
        String normalizedName = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (!normalizedName.endsWith(".xlsx") && !normalizedName.endsWith(".xls")) {
            throw new BusinessException("仅支持 .xlsx / .xls 格式的 Excel 文件");
        }
        findCampaign(campaignId);

        List<ExamCandidateImportRow> rows = parseImportRows(fileBytes);
        if (rows.isEmpty()) {
            throw new BusinessException("表格中没有可导入的考生数据（姓名、账号均为空的行会被跳过）");
        }

        List<ExamCandidateImportOutcome> outcomes = new ArrayList<>(rows.size());
        int successCount = 0;
        for (ExamCandidateImportRow row : rows) {
            try {
                String username = createFromImportRow(campaignId, row);
                successCount++;
                outcomes.add(new ExamCandidateImportOutcome(row.rowNumber(), row.name(), username, true, null));
            } catch (Exception exception) {
                String reason = exception instanceof BusinessException business
                        ? business.getMessage() : "导入失败，请检查该行内容";
                outcomes.add(new ExamCandidateImportOutcome(row.rowNumber(), row.name(), null, false, reason));
            }
        }
        return new ExamCandidateImportResult(rows.size(), successCount, rows.size() - successCount, outcomes);
    }

    /** 单行导入：复用单个创建逻辑（建号 + 独立考生空间 + 报名），账号/密码可留空自动生成。 */
    private String createFromImportRow(Long campaignId, ExamCandidateImportRow row) {
        String nickname = row.name() == null ? "" : row.name().trim();
        String username = row.username() == null || row.username().isBlank()
                ? generateUsername() : row.username().trim();
        String plainPassword = row.password() == null || row.password().isBlank()
                ? DEFAULT_PASSWORD : row.password().trim();
        if (plainPassword.length() < 6 || plainPassword.length() > 20) {
            throw new BusinessException("密码长度需为 6～20 位");
        }

        ExamCandidateAccountCreateRequest request = new ExamCandidateAccountCreateRequest();
        request.setNickname(nickname);
        request.setUsername(username);
        request.setPassword(CryptoUtils.md5(plainPassword));
        request.setOrganization(row.organization() == null || row.organization().isBlank()
                ? null : row.organization().trim());
        // 直接走内部创建（无外层事务）：单行失败只影响本行，其他行继续导入。
        withoutTenantScope(() -> createInternal(campaignId, request));
        return username;
    }

    /** 自动生成考生账号：exam + 6 位小写字母数字，查重失败时多次重试。 */
    private String generateUsername() {
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder token = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                token.append(USERNAME_ALPHABET.charAt(RANDOM.nextInt(USERNAME_ALPHABET.length())));
            }
            String candidate = "exam" + token;
            Long count = accountMapper.selectCount(new LambdaQueryWrapper<Account>()
                    .eq(Account::getUsername, candidate));
            if (count != null && count == 0) {
                return candidate;
            }
        }
        return "exam" + CryptoUtils.uuid().replace("-", "").substring(0, 10);
    }

    /** 解析名单 Excel：A=姓名（必填） B=账号 C=初始密码 D=组织；首行含「姓名」视为表头。 */
    private List<ExamCandidateImportRow> parseImportRows(byte[] fileBytes) {
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(fileBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            List<ExamCandidateImportRow> rows = new ArrayList<>();
            int firstRowNum = sheet.getFirstRowNum();
            for (int i = firstRowNum; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String name = formatter.formatCellValue(row.getCell(0)).trim();
                String username = formatter.formatCellValue(row.getCell(1)).trim();
                if (name.isEmpty() && username.isEmpty()) {
                    continue;
                }
                if (i == firstRowNum && name.contains("姓名")) {
                    continue;
                }
                if (rows.size() >= MAX_IMPORT_ROWS) {
                    throw new BusinessException("单次最多导入 " + MAX_IMPORT_ROWS + " 名考生，请分批导入");
                }
                rows.add(new ExamCandidateImportRow(
                        i + 1,
                        name,
                        username,
                        formatter.formatCellValue(row.getCell(2)).trim(),
                        formatter.formatCellValue(row.getCell(3)).trim()));
            }
            return rows;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("Excel 解析失败，请下载系统提供的导入模板后重试");
        }
    }

    @Override
    public byte[] buildImportTemplate() {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("考生导入模板");
            String[] header = {"姓名（必填）", "账号（留空自动生成）", "初始密码（留空为 12345678）", "组织/单位（选填）"};
            Row headerRow = sheet.createRow(0);
            for (int column = 0; column < header.length; column++) {
                headerRow.createCell(column).setCellValue(header[column]);
            }
            String[][] samples = {
                    {"张三", "exam0001", "12345678", "示例单位"},
                    {"李四", "", "", ""},
            };
            for (int rowIndex = 0; rowIndex < samples.length; rowIndex++) {
                Row row = sheet.createRow(rowIndex + 1);
                for (int column = 0; column < samples[rowIndex].length; column++) {
                    row.createCell(column).setCellValue(samples[rowIndex][column]);
                }
            }
            sheet.createRow(samples.length + 2).createCell(0).setCellValue(
                    "说明：姓名必填且不超过 10 个字符；账号 4～20 位字母数字（留空自动生成）；密码 6～20 位（留空统一为 12345678）；单次最多导入 200 行。");
            for (int column = 0; column < 4; column++) {
                sheet.setColumnWidth(column, 30 * 256);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new BusinessException("生成导入模板失败，请稍后重试");
        }
    }

    private Long createInternal(Long campaignId, ExamCandidateAccountCreateRequest request) {
        ExamCampaign campaign = findCampaign(campaignId);
        if (request == null) {
            throw new BusinessException("请填写考生账号信息");
        }

        String nickname = required(request.getNickname(), "请填写考生姓名");
        String username = required(request.getUsername(), "请填写考生账号");
        String email = normalizeEmail(request.getEmail(), username);
        validateAccountFields(nickname, username, email);
        validatePasswordDigest(request.getPassword());

        RegisterRequest register = new RegisterRequest();
        register.setNickname(nickname);
        register.setUsername(username);
        register.setEmail(email);
        register.setPassword(request.getPassword());
        register.setCreateTenant(true);
        register.setTenantName("考生空间-" + nickname);
        register.setTenantCode(buildTenantCode(username));
        register.setTenantDescription("考试考生独立工作空间");
        accountService.register(register);

        // 创建租户时初始化服务会清理线程上下文，后续报名写入需重新忽略租户过滤。
        TenantUtils.setIgnoreTenant();
        Account account = accountMapper.selectOne(new LambdaQueryWrapper<Account>()
                .eq(Account::getUsername, username));
        if (account == null || account.getLastTenantId() == null) {
            throw new BusinessException("考生账号创建失败，请重试");
        }

        ExamEnrollment enrollment = new ExamEnrollment();
        enrollment.setTenantId(account.getLastTenantId());
        enrollment.setCampaignId(campaign.getId());
        enrollment.setAccountId(account.getId());
        enrollment.setName(nickname);
        enrollment.setOrganization(trimToNull(request.getOrganization()));
        enrollment.setStatus("REGISTERED");
        enrollment.setEnabled(true);
        if (enrollmentMapper.insert(enrollment) != 1) {
            throw new BusinessException("考生加入考试失败，请重试");
        }
        return enrollment.getId();
    }

    private boolean updateInternal(Long campaignId, Long enrollmentId,
                                   ExamCandidateAccountUpdateRequest request) {
        if (request == null) {
            throw new BusinessException("请填写考生账号信息");
        }
        ExamEnrollment enrollment = findEnrollment(campaignId, enrollmentId);
        Account account = findAccount(enrollment.getAccountId());
        ensureNotPlatformAdmin(account);

        String nickname = required(request.getNickname(), "请填写考生姓名");
        String username = required(request.getUsername(), "请填写考生账号");
        String email = normalizeEmail(request.getEmail(), username);
        validateAccountFields(nickname, username, email);
        ensureUniqueAccountFields(account.getId(), username, email);

        account.setNickname(nickname);
        account.setUsername(username);
        account.setEmail(email);
        enrollment.setName(nickname);
        enrollment.setOrganization(trimToNull(request.getOrganization()));
        int accountUpdated = accountMapper.updateById(account);
        int enrollmentUpdated = enrollmentMapper.updateById(enrollment);
        if (accountUpdated != 1 || enrollmentUpdated != 1) {
            throw new BusinessException("考生账号更新失败，请重试");
        }
        return true;
    }

    private ExamCampaign findCampaign(Long campaignId) {
        if (campaignId == null) {
            throw new BusinessException("考试批次不能为空");
        }
        ExamCampaign campaign = campaignMapper.selectById(campaignId);
        if (campaign == null) {
            throw new BusinessException("考试批次不存在");
        }
        return campaign;
    }

    private ExamEnrollment findEnrollment(Long campaignId, Long enrollmentId) {
        if (campaignId == null || enrollmentId == null) {
            throw new BusinessException("考试批次或考生不能为空");
        }
        ExamEnrollment enrollment = enrollmentMapper.selectOne(
                new LambdaQueryWrapper<ExamEnrollment>()
                        .eq(ExamEnrollment::getId, enrollmentId)
                        .eq(ExamEnrollment::getCampaignId, campaignId)
                        .eq(ExamEnrollment::getEnabled, true)
                        .eq(ExamEnrollment::getStatus, "REGISTERED")
                        .apply("del_flag = 0"));
        if (enrollment == null) {
            throw new BusinessException("考生账号不存在或已移出本场考试");
        }
        return enrollment;
    }

    private Account findAccount(Long accountId) {
        Account account = accountId == null ? null : accountMapper.selectById(accountId);
        if (account == null) {
            throw new BusinessException("考生账号不存在");
        }
        return account;
    }

    private void validateAccountFields(String nickname, String username, String email) {
        if (nickname.length() > 10) {
            throw new BusinessException("考生姓名不能超过 10 个字符");
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new BusinessException("账号需为 4～20 位字母、数字、点、下划线或短横线");
        }
        if (email.length() > 100 || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new BusinessException("邮箱格式不正确");
        }
    }

    private void ensureUniqueAccountFields(Long accountId, String username, String email) {
        long usernameCount = accountMapper.selectCount(new LambdaQueryWrapper<Account>()
                .eq(Account::getUsername, username)
                .ne(Account::getId, accountId));
        if (usernameCount > 0) {
            throw new BusinessException("账号已存在");
        }
        long emailCount = accountMapper.selectCount(new LambdaQueryWrapper<Account>()
                .eq(Account::getEmail, email)
                .ne(Account::getId, accountId));
        if (emailCount > 0) {
            throw new BusinessException("邮箱已被使用");
        }
    }

    private void validatePasswordDigest(String password) {
        if (password == null || !MD5_PATTERN.matcher(password).matches()) {
            throw new BusinessException("密码格式不正确");
        }
    }

    private void ensureNotPlatformAdmin(Account account) {
        if (UserUtils.isPlatformAdminUsername(account.getUsername())) {
            throw new BusinessException("平台管理员账号不可在考试中修改或删除");
        }
    }

    private String normalizeEmail(String email, String username) {
        String value = trimToNull(email);
        return value == null ? username.toLowerCase(Locale.ROOT) + "@exam.local" : value;
    }

    private String buildTenantCode(String username) {
        String normalized = username.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-");
        if (normalized.length() > 28) {
            normalized = normalized.substring(0, 28);
        }
        return "exam-" + normalized + "-" + CryptoUtils.uuid().substring(0, 8);
    }

    private String required(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BusinessException(message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private <T> T withoutTenantScope(Supplier<T> action) {
        Long previousTenantId = TenantUtils.getCurrentTenantId();
        String previousTenantCode = TenantUtils.getCurrentTenantCode();
        boolean previousIgnoreTenant = TenantUtils.isIgnoreTenant();
        TenantUtils.setIgnoreTenant();
        try {
            return action.get();
        } finally {
            TenantUtils.clear();
            if (previousTenantId != null) {
                TenantUtils.setCurrentTenant(previousTenantId, previousTenantCode);
            }
            if (previousIgnoreTenant) {
                TenantUtils.setIgnoreTenant();
            }
        }
    }
}
