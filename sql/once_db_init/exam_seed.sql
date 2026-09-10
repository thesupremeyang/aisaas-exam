-- 考试版专用种子数据。
-- 本文件只应在 apboa_exam 中执行，不包含任何 SaaS 版业务数据。
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

START TRANSACTION;

-- 2026-08-29 11:30—13:30，考试窗口总时长为 2 小时。
UPDATE exam_campaign
SET name = '2026 智能体实操考试（考试版）',
    registration_start = '2026-08-01 00:00:00',
    registration_end = '2026-08-31 23:59:59',
    submission_start = '2026-08-29 11:30:00',
    submission_end = '2026-08-29 13:30:00',
    review_end = '2026-09-30 23:59:59',
    status = 'OPEN',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 20260801000001;

-- 两个考试版演示考生账号。密码字段保存的是平台兼容的摘要值。
-- 考试账号需要在考试版租户内创建作品，因此授予租户编辑权限；账号仍只存在于考试版数据库。
INSERT INTO account
  (id, nickname, email, username, password, enabled, created_at, updated_at,
   created_by, updated_by, remember_last_tenant, last_tenant_id)
VALUES
  (2090000000000000001, '模拟考生1', 'candidate01@exam.local', '20260001',
   'e10adc3949ba59abbe56e057f20f883e', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
   1111111111111111111, 1111111111111111111, 1, 1),
  (2090000000000000002, '模拟考生2', 'candidate02@exam.local', '20260002',
   'e10adc3949ba59abbe56e057f20f883e', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
   1111111111111111111, 1111111111111111111, 1, 1)
ON DUPLICATE KEY UPDATE
  nickname = VALUES(nickname), email = VALUES(email), username = VALUES(username),
  password = VALUES(password), enabled = VALUES(enabled),
  updated_at = CURRENT_TIMESTAMP, remember_last_tenant = VALUES(remember_last_tenant),
  last_tenant_id = VALUES(last_tenant_id);

INSERT INTO account_tenant
  (id, account_id, tenant_id, role, enabled, created_at, updated_at)
VALUES
  (2090000000000000101, 2090000000000000001, 1, 'TENANT_EDITOR', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (2090000000000000102, 2090000000000000002, 1, 'TENANT_EDITOR', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE
  account_id = VALUES(account_id), tenant_id = VALUES(tenant_id), role = VALUES(role),
  enabled = VALUES(enabled), updated_at = CURRENT_TIMESTAMP;

INSERT INTO exam_enrollment
  (id, tenant_id, campaign_id, account_id, name, phone_ciphertext, id_no_ciphertext,
   organization, status, entered_at, enabled, del_flag, created_at, updated_at,
   created_by, updated_by)
VALUES
  (2090000000000000201, 1, 20260801000001, 2090000000000000001, '模拟考生1', NULL, NULL,
   '考试版演示组', 'REGISTERED', NULL, 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
   1111111111111111111, 1111111111111111111),
  (2090000000000000202, 1, 20260801000001, 2090000000000000002, '模拟考生2', NULL, NULL,
   '考试版演示组', 'REGISTERED', NULL, 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
   1111111111111111111, 1111111111111111111)
ON DUPLICATE KEY UPDATE
  name = VALUES(name), organization = VALUES(organization), status = VALUES(status),
  enabled = VALUES(enabled), del_flag = VALUES(del_flag), updated_at = CURRENT_TIMESTAMP;

INSERT INTO exam_submission
  (id, tenant_id, enrollment_id, agent_id, work_type, form_json, current_step,
   status, version, submitted_at, enabled, del_flag, created_at, updated_at,
   created_by, updated_by)
VALUES
  (2090000000000000301, 1, 2090000000000000201, NULL, 'AGENT', NULL, 1,
   'DRAFT', 0, NULL, 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
   2090000000000000001, 2090000000000000001),
  (2090000000000000302, 1, 2090000000000000202, NULL, 'AGENT', NULL, 1,
   'DRAFT', 0, NULL, 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
   2090000000000000002, 2090000000000000002)
ON DUPLICATE KEY UPDATE
  tenant_id = VALUES(tenant_id), agent_id = VALUES(agent_id), work_type = VALUES(work_type),
  current_step = VALUES(current_step), status = VALUES(status), enabled = VALUES(enabled),
  del_flag = VALUES(del_flag), updated_at = CURRENT_TIMESTAMP;

COMMIT;
SET FOREIGN_KEY_CHECKS = 1;
