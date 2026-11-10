-- AI 阅卷模型配置（全局单行，平台级；表在 MybatisPlusConfig.IGNORE_TABLES 中，不做租户过滤）
CREATE TABLE IF NOT EXISTS `ai_grading_settings` (
  `id` bigint NOT NULL,
  `endpoint` varchar(500) NOT NULL COMMENT 'OpenAI 兼容 chat/completions 地址',
  `api_key` varchar(500) NOT NULL COMMENT 'API Key',
  `model` varchar(200) NOT NULL COMMENT '模型名',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_by` bigint NULL DEFAULT NULL,
  `updated_by` bigint NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 阅卷模型配置';

-- 初始行只是占位：密钥不写入仓库、不写死在初始化脚本里。
-- 部署后请通过环境变量 ASSESSMENT_GRADING_API_KEY / 管理端「AI 阅卷设置」
-- 填入自己的 Key，并把 enabled 置为 1；未配置时 AI 阅卷入队会被守卫拦截。
INSERT IGNORE INTO `ai_grading_settings`
  (`id`, `endpoint`, `api_key`, `model`, `enabled`, `created_at`, `updated_at`)
VALUES
  (1, 'https://api.deepseek.com/chat/completions', '', 'deepseek-flash', 0, NOW(), NOW());
