-- 查分模式成绩字段：执行一次即可。
-- 查分开关复用 exam_campaign.config_json，不新增考试批次表字段。
ALTER TABLE exam_enrollment
  ADD COLUMN theory_score INT NULL COMMENT '理论考试原始分（0-100）',
  ADD COLUMN practical_score INT NULL COMMENT '实操考试原始分（0-100）';
