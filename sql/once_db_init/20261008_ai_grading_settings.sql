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

-- 初始行：沿用当前 DeepSeek 配置（迁移自 application-exam-local.yml / 启动脚本 env）
INSERT IGNORE INTO `ai_grading_settings`
  (`id`, `endpoint`, `api_key`, `model`, `enabled`, `created_at`, `updated_at`)
VALUES
  (1, 'https://api.deepseek.com/chat/completions', 'REPLACE_WITH_YOUR_DEEPSEEK_API_KEY', 'deepseek-flash', 1, NOW(), NOW());
