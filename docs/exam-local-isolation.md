# 考试版本地环境隔离

考试版和现有 SaaS 版共用代码，但不能共用运行数据。考试版使用以下独立资源：

| 资源 | SaaS 版 | 考试版 |
| --- | --- | --- |
| MySQL | `apboa_next` | `apboa_exam` |
| PostgreSQL 向量库 | `apboa_vector` | `apboa_exam_vector` |
| Redis 数据库 | `7` | `8` |
| 后端端口 | `3060`—`3064` | `3160`—`3164` |
| UI 端口 | `4000` | `4100` |
| 本地工作目录 | `.apboa` | `.apboa-exam` |

## 初始化

在项目根目录执行：

```bash
./script/init-exam-local-db.sh
```

脚本只允许操作 `apboa_exam` 和 `apboa_exam_vector`。MySQL 使用 [sql/once_db_init/apboa_exam.sql](../sql/once_db_init/apboa_exam.sql) 这份完整考试版数据库快照，其中包含当前考试版所需的表结构和演示数据；脚本不会读取或复制 `apboa_next` 中的数据。

`sql/once_db_init/` 中的旧基础结构和增量脚本仅作为历史留档，考试版初始化脚本不会再重复执行它们。

## 启动

确保 Maven、MySQL、PostgreSQL、Redis 和 Node.js 已启动后执行：

```bash
./script/start-exam-local.sh
```

考试版 UI 地址为 `http://localhost:4100/web/`。现有 SaaS 版的 `3060`—`3064` 和 `4000` 端口不应被这个脚本停止或覆盖。

默认考试批次的提交窗口为 `2026-08-29 11:30:00` 至 `13:30:00`，总时长两小时；正式考试日期如需调整，应在考试版管理员端修改考试批次，不要改回 SaaS 数据库。
