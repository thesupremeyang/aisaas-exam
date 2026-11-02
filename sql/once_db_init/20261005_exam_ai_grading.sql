-- AI 阅卷（配置评分）功能：任务表 + 结果表
-- 评分对象是考生智能体的真实配置（agent_definition 提示词 + 知识库文档切片 + Skill 文件），
-- 不读取截图/视频等运行证据；分数为纯配置量表分，另存折算百分制参考分。

CREATE TABLE IF NOT EXISTS `exam_grading_task` (
  `id` bigint NOT NULL, `tenant_id` bigint NOT NULL, `campaign_id` bigint NOT NULL, `submission_id` bigint NOT NULL,
  `status` varchar(32) NOT NULL, `progress` int NOT NULL DEFAULT 0,
  `error_code` varchar(64) DEFAULT NULL, `error_message` varchar(1000) DEFAULT NULL,
  `started_at` datetime DEFAULT NULL, `finished_at` datetime DEFAULT NULL,
  `enabled` tinyint NOT NULL DEFAULT 1, `del_flag` tinyint NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL, `updated_at` datetime NOT NULL, `created_by` bigint DEFAULT NULL, `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_exam_grading_task_status` (`status`,`created_at`),
  KEY `idx_exam_grading_task_submission` (`submission_id`,`status`),
  KEY `idx_exam_grading_task_campaign` (`campaign_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 阅卷任务（配置评分）';

CREATE TABLE IF NOT EXISTS `exam_grading_result` (
  `id` bigint NOT NULL, `tenant_id` bigint NOT NULL,
  `submission_id` bigint NOT NULL, `task_id` bigint NOT NULL, `agent_id` bigint DEFAULT NULL, `question_id` bigint DEFAULT NULL,
  `base_score` decimal(5,1) NOT NULL DEFAULT 0, `coefficient` decimal(4,2) NOT NULL DEFAULT 1.00,
  `cap_ratio` decimal(4,2) DEFAULT NULL, `cap_status` varchar(32) DEFAULT NULL,
  `dominance_status` varchar(32) DEFAULT NULL,
  `total_score` decimal(5,1) NOT NULL DEFAULT 0, `config_max_score` decimal(5,1) NOT NULL DEFAULT 0,
  `normalized_score` decimal(5,1) DEFAULT NULL,
  `confidence` varchar(16) DEFAULT NULL, `rubric_version` varchar(64) NOT NULL DEFAULT 'config-v1',
  `prompt_version` varchar(64) NOT NULL DEFAULT 'grading-v1', `model_name` varchar(200) DEFAULT NULL,
  `report_json` json NOT NULL,
  `enabled` tinyint NOT NULL DEFAULT 1, `del_flag` tinyint NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL, `updated_at` datetime NOT NULL, `created_by` bigint DEFAULT NULL, `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_exam_grading_result_submission` (`submission_id`,`created_at`),
  KEY `idx_exam_grading_result_campaign_q` (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 阅卷结果（配置评分）';
