-- 考生状态管理：记录考生首次进入开放考试窗口的时间。
SET @entered_at_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'exam_enrollment'
    AND column_name = 'entered_at'
);
SET @entered_at_sql = IF(
  @entered_at_exists = 0,
  'ALTER TABLE exam_enrollment ADD COLUMN entered_at datetime DEFAULT NULL AFTER status',
  'SELECT 1'
);
PREPARE entered_at_stmt FROM @entered_at_sql;
EXECUTE entered_at_stmt;
DEALLOCATE PREPARE entered_at_stmt;
