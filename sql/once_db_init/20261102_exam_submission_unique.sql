-- 2026-11-02 并发加固：重复答卷唯一约束
--
-- 背景：CandidateExamServiceImpl.createSubmission() 是 check-then-act
-- （先 selectCount 再 insert），双击、前端重试、开两个标签页都会插入两条 DRAFT。
-- 历史事故：9/5 出现同一考生同一题 2~3 条提交，成绩以哪条为准取决于查询顺序。
--
-- MySQL 没有部分索引，用生成列实现"仅未删除记录唯一"：
-- del_flag=1（已删除）时 active_pair 为 NULL，不参与唯一性，可以删了重建。
--
-- 执行前确认：SELECT enrollment_id, question_id, COUNT(*) c FROM exam_submission
--            WHERE del_flag=0 GROUP BY 1,2 HAVING c>1;   -- 必须为空

ALTER TABLE `exam_submission`
  ADD COLUMN `active_pair` VARCHAR(64)
    GENERATED ALWAYS AS (IF(del_flag = 0, CONCAT(enrollment_id, '-', question_id), NULL)) STORED,
  ADD UNIQUE KEY `uk_exam_submission_active` (`active_pair`);

-- 代码侧配套：CandidateExamServiceImpl 捕获 DuplicateKeyException 后
-- 返回已存在的那条记录（幂等），而不是抛错给考生。
