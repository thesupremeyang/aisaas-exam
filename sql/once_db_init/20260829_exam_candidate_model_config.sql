-- 为已创建的考试库增加考生专属模型配置关联。
ALTER TABLE `exam_enrollment`
  ADD COLUMN `model_config_id` bigint DEFAULT NULL COMMENT '考生专属模型配置ID' AFTER `entered_at`;
