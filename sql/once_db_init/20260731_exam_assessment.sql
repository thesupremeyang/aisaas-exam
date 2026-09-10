-- 智评工坊考试门户（MVP）
CREATE TABLE IF NOT EXISTS `exam_campaign` (
  `id` bigint NOT NULL, `tenant_id` bigint NOT NULL, `name` varchar(200) NOT NULL,
  `registration_start` datetime DEFAULT NULL, `registration_end` datetime DEFAULT NULL,
  `submission_start` datetime DEFAULT NULL, `submission_end` datetime DEFAULT NULL,
  `review_end` datetime DEFAULT NULL, `status` varchar(32) NOT NULL,
  `rubric_version` varchar(64) NOT NULL, `config_json` json DEFAULT NULL,
  `enabled` tinyint NOT NULL DEFAULT 1, `del_flag` tinyint NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL, `updated_at` datetime NOT NULL, `created_by` bigint DEFAULT NULL, `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`), KEY `idx_exam_campaign_tenant_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试批次';

CREATE TABLE IF NOT EXISTS `exam_enrollment` (
  `id` bigint NOT NULL, `tenant_id` bigint NOT NULL, `campaign_id` bigint NOT NULL, `account_id` bigint NOT NULL,
  `name` varchar(50) NOT NULL, `phone_ciphertext` varchar(512) DEFAULT NULL, `id_no_ciphertext` varchar(512) DEFAULT NULL,
  `organization` varchar(200) DEFAULT NULL, `status` varchar(32) NOT NULL,
  `theory_score` int DEFAULT NULL COMMENT '理论考试原始分（0-100）',
  `practical_score` int DEFAULT NULL COMMENT '实操考试原始分（0-100）',
  `entered_at` datetime DEFAULT NULL, `model_config_id` bigint DEFAULT NULL COMMENT '考生专属模型配置ID',
  `enabled` tinyint NOT NULL DEFAULT 1, `del_flag` tinyint NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL, `updated_at` datetime NOT NULL, `created_by` bigint DEFAULT NULL, `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_exam_enrollment_campaign_account` (`campaign_id`,`account_id`,`del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试报名';

CREATE TABLE IF NOT EXISTS `exam_submission` (
  `id` bigint NOT NULL, `tenant_id` bigint NOT NULL, `enrollment_id` bigint NOT NULL, `agent_id` bigint DEFAULT NULL,
  `work_type` varchar(32) NOT NULL, `form_json` json DEFAULT NULL, `current_step` int NOT NULL DEFAULT 1,
  `status` varchar(32) NOT NULL, `version` int NOT NULL DEFAULT 0, `submitted_at` datetime DEFAULT NULL,
  `enabled` tinyint NOT NULL DEFAULT 1, `del_flag` tinyint NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL, `updated_at` datetime NOT NULL, `created_by` bigint DEFAULT NULL, `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`), KEY `idx_exam_submission_status` (`status`,`submitted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试作品提交';

CREATE TABLE IF NOT EXISTS `assessment_task` (
  `id` bigint NOT NULL, `tenant_id` bigint NOT NULL, `submission_id` bigint NOT NULL, `task_type` varchar(32) NOT NULL,
  `status` varchar(32) NOT NULL, `progress` int NOT NULL DEFAULT 0, `attempt` int NOT NULL DEFAULT 0,
  `input_hash` varchar(128) DEFAULT NULL, `error_code` varchar(64) DEFAULT NULL, `error_message` varchar(1000) DEFAULT NULL,
  `started_at` datetime DEFAULT NULL, `finished_at` datetime DEFAULT NULL,
  `enabled` tinyint NOT NULL DEFAULT 1, `del_flag` tinyint NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL, `updated_at` datetime NOT NULL, `created_by` bigint DEFAULT NULL, `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`), KEY `idx_assessment_task_status` (`status`,`created_at`), KEY `idx_assessment_task_submission` (`submission_id`,`task_type`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI评测任务';

CREATE TABLE IF NOT EXISTS `exam_sso_ticket` (
  `id` bigint NOT NULL, `tenant_id` bigint NOT NULL, `jti` varchar(64) NOT NULL,
  `ticket_hash` char(64) NOT NULL, `account_id` bigint NOT NULL, `campaign_id` bigint NOT NULL,
  `return_url` varchar(1000) NOT NULL, `expires_at` datetime NOT NULL, `consumed_at` datetime DEFAULT NULL,
  `enabled` tinyint NOT NULL DEFAULT 1, `del_flag` tinyint NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL, `updated_at` datetime NOT NULL, `created_by` bigint DEFAULT NULL, `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_exam_sso_ticket_hash` (`ticket_hash`),
  UNIQUE KEY `uk_exam_sso_ticket_jti` (`jti`), KEY `idx_exam_sso_ticket_expiry` (`expires_at`,`consumed_at`),
  KEY `idx_exam_sso_ticket_subject` (`tenant_id`,`account_id`,`campaign_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试门户一次性 SSO 凭证';

CREATE TABLE IF NOT EXISTS `assessment_report` (
  `id` bigint NOT NULL, `tenant_id` bigint NOT NULL, `submission_id` bigint NOT NULL, `task_id` bigint NOT NULL,
  `rubric_version` varchar(64) NOT NULL, `prompt_version` varchar(64) NOT NULL, `model_name` varchar(200) DEFAULT NULL,
  `report_json` json NOT NULL, `total_score` int NOT NULL, `confidence` varchar(16) NOT NULL,
  `enabled` tinyint NOT NULL DEFAULT 1, `del_flag` tinyint NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL, `updated_at` datetime NOT NULL, `created_by` bigint DEFAULT NULL, `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_assessment_report_submission` (`submission_id`,`del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI初评报告';

CREATE TABLE IF NOT EXISTS `assessment_review` (
  `id` bigint NOT NULL, `tenant_id` bigint NOT NULL, `submission_id` bigint NOT NULL, `reviewer_account_id` bigint DEFAULT NULL,
  `scores_json` json DEFAULT NULL, `total_score` int DEFAULT NULL, `comment` varchar(2000) DEFAULT NULL,
  `checklist_json` json DEFAULT NULL, `status` varchar(32) NOT NULL,
  `enabled` tinyint NOT NULL DEFAULT 1, `del_flag` tinyint NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL, `updated_at` datetime NOT NULL, `created_by` bigint DEFAULT NULL, `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_assessment_review_submission` (`submission_id`,`del_flag`),
  KEY `idx_assessment_review_status` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评委复核';

CREATE TABLE IF NOT EXISTS `assessment_result` (
  `id` bigint NOT NULL, `tenant_id` bigint NOT NULL, `submission_id` bigint NOT NULL,
  `final_score` int NOT NULL, `level` varchar(32) NOT NULL, `published_by` bigint DEFAULT NULL,
  `published_at` datetime DEFAULT NULL, `version` int NOT NULL DEFAULT 0,
  `enabled` tinyint NOT NULL DEFAULT 1, `del_flag` tinyint NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL, `updated_at` datetime NOT NULL, `created_by` bigint DEFAULT NULL, `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_assessment_result_submission` (`submission_id`,`del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='最终评分结果';

-- 演示批次（tenant_id 默认 1；按需调整）
INSERT INTO exam_campaign (id, tenant_id, name,
  registration_start, registration_end, submission_start, submission_end,
  review_end, status, rubric_version, config_json, enabled, del_flag, created_at, updated_at)
VALUES (20260801000001, 1, '2026 智能体开发专题班',
  '2026-08-01 00:00:00', '2026-08-31 23:59:59',
  '2026-08-01 00:00:00', '2026-09-20 23:59:59',
  '2026-09-30 23:59:59', 'OPEN', 'v1', JSON_OBJECT(
    '评分规则', JSON_OBJECT(
      '评分维度', JSON_OBJECT(
        '场景价值', 20,
        '功能完整度', 20,
        '创新性', 20,
        '技术说明', 20,
        '可验证性', 20
      ),
      '等级线', JSON_OBJECT(
        '优秀', 90,
        '良好', 75,
        '合格', 60
      )
    )
  ), 1, 0, NOW(), NOW());

CREATE TABLE IF NOT EXISTS `exam_question` (
  `id` bigint NOT NULL, `tenant_id` bigint NOT NULL, `campaign_id` bigint NOT NULL,
  `title` varchar(500) NOT NULL COMMENT '题目',
  `score` int NOT NULL DEFAULT 0 COMMENT '分值',
  `suggested_minutes` int DEFAULT NULL COMMENT '建议用时（分钟）',
  `task_scenario` text DEFAULT NULL COMMENT '任务场景',
  `task_requirement` text DEFAULT NULL COMMENT '任务要求',
  `submission_deliverable` text DEFAULT NULL COMMENT '提交成果',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序',
  `enabled` tinyint NOT NULL DEFAULT 1, `del_flag` tinyint NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL, `updated_at` datetime NOT NULL,
  `created_by` bigint DEFAULT NULL, `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`), KEY `idx_exam_question_campaign` (`campaign_id`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试试题';
