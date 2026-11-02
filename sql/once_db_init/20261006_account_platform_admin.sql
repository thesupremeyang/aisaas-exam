-- 用户管理：平台管理员标记落库（此前平台管理员仅由环境变量 APBOA_PLATFORM_ADMIN_USERNAMES 决定，
-- 界面无法新增管理员账号；改为 DB 标记驱动，环境变量保留作兜底）

ALTER TABLE `account`
  ADD COLUMN `platform_admin` tinyint NOT NULL DEFAULT 0 COMMENT '是否平台管理员' AFTER `remember_last_tenant`;

-- 内置 admin 账号补上平台管理员标记（豁免判定改为纯 DB 标记驱动，生产部署时必须执行）
UPDATE `account` SET `platform_admin` = 1 WHERE `id` = 1111111111111111111;
