# 智能体考试系统 · 并发测试 Runbook

> 配套脚本：`exam-sse.mjs`（对话 SSE）、`ws-probe.mjs`（WS 接入）、`agent-heal.mjs`（注册自愈）
> 目标不是"压出 QPS 数字"，而是回答三个问题：
> 1. 考试时间轴上的尖峰能不能过；
> 2. 长连接 + 上游抖动时，系统是**降级**还是**整片静默失败**；
> 3. 并发下数据有没有被写坏。

---

## 0. 通过标准（先定，再看数）

| 指标 | 75 人（基线） | 225 人（3×） |
|---|---|---|
| HTTP 错误率 | < 0.1% | < 0.5% |
| 对话完成率（收到 RUN_FINISHED） | > 99.5% | > 99% |
| 首 token 延迟 p95 | < 3 s | < 8 s |
| 单轮对话总时长 p95 | < 30 s | < 60 s |
| 交卷成功率 | 100% | 100% |
| 重复答卷 / 重复阅卷 | 0 | 0 |
| 2.5 h 堆增长 | < 20% | < 30% |

任一项不达标即判不通过，不要用"平均值还行"掩盖。

---

## 1. 前置条件

1. **独立场次**：新建一个只给压测用的 campaign，别碰真实考生数据。
2. **压测账号**：N 个考生账号，每个账号下有一个可用 agent（模型指向平台内置、可用的那个）。
3. **LLM 策略**（关键）：
   - 第一轮用 **mock/低配额模型**，测平台自身并发；
   - 第二轮接真实上游，只跑 10–20 并发验证端到端；
   - 不要用真实上游跑 200 并发——你测到的是厂商限流，不是自己的系统。
4. **观测就位**：`top -H`、`jstat -gc`、MySQL `SHOW PROCESSLIST`、`ss -s`、nginx `$request_time`。
5. **随时可回滚**：先把 console/runtime 的 jar 和 `apboa.env` 备份一份。

---

## 2. 第一步：WS 接入探针（1 分钟，先排除 9/5 那类路由事故）

```bash
node ws-probe.mjs --base=http://127.0.0.1:18080/web/api --n=60 --concurrency=30
```

判读：

| 结果 | 含义 |
|---|---|
| `101` / `401` 为主 | 路由正确、服务健康（401 只是没带 token） |
| **`404`** | **nginx 缺 `/web/api/ws/`，前端会无限重连 —— 立刻停，先修配置** |
| `502/504` | 上游 websocket 服务异常或超时 |

---

## 3. 第二步：造会话

```bash
export TOKEN=<考生 JWT>
node exam-sse.mjs --base=http://127.0.0.1:18080/web/api --token=$TOKEN \
  --agent-code=exam-candidate-xxxx --init=40 --out=artifacts/sessions-40.json
```

> 一个会话只能被一个"考生"用。不要多个 VU 共用一个 threadId —— 
> runtime 对同一 threadId 只允许一个活跃 run，会把并发压成串行。

---

## 4. 第三步：注册自愈验证（每次发版后必跑）

```bash
node agent-heal.mjs --base=http://127.0.0.1:18080/web/api --token=$TOKEN \
  --sessions=artifacts/sessions-40.json --repeat=2
```

- 期望：全部 `resolved`；
- 出现 `agent_not_found` ⇒ 自愈/对账没生效，**不要开考**。

---

## 5. 第四步：SSE 对话压测（分档）

```bash
# 基线：40 VU × 3 轮
node exam-sse.mjs --base=http://127.0.0.1:18080/web/api --token=$TOKEN \
  --sessions=artifacts/sessions-40.json --vus=40 --rounds=3 \
  --jtl=artifacts/sse-40.jtl --summary=artifacts/sse-40.json

# 尖峰：同时开跑（不 ramp），模拟开考瞬间
node exam-sse.mjs ... --vus=225 --rounds=1

# 长稳：40 VU × 60 轮（等同于 2 小时考试的量级）
node exam-sse.mjs ... --vus=40 --rounds=60 --think-ms=30000
```

脚本退出码：`0` 通过；`1` 错误率 >1% 或完成率 <99%；`2` 参数/网络错误；`3`（agent-heal）注册失败。

每一档之间**清一次数据**，否则草稿越积越多，第二档测的是"上一档的垃圾"。

---

## 6. 第五步：并发正确性断言（压完必须跑）

```sql
-- 同一考生同一题只能有一条有效提交
SELECT enrollment_id, question_id, COUNT(*) c
FROM exam_submission WHERE del_flag=0
GROUP BY 1,2 HAVING c>1;

-- 已交卷不能回退成草稿
SELECT COUNT(*) FROM exam_submission
WHERE status='DRAFT' AND submitted_at IS NOT NULL;

-- 同一份卷只能有一条在跑的阅卷任务
SELECT submission_id, COUNT(*) c FROM exam_grading_task
WHERE status IN ('QUEUED','RUNNING') GROUP BY 1 HAVING c>1;
```

这三条只要有非空结果，就是**数据被写坏**，比任何延迟指标都严重。

---

## 7. 第六步：故障注入（决定"崩的时候有多难看"）

| 注入 | 命令 | 期望 |
|---|---|---|
| console 进程被杀 | `systemctl kill -s SIGKILL apboa-console` | 考生看到明确错误，恢复后能继续；不是白屏 |
| runtime 被杀 | 同上 `apboa-runtime` | 进行中的对话给出错误事件；重连后能继续 |
| Redis 抖动 | `tc qdisc add dev lo root netem delay 300ms` | 鉴权变慢但不雪崩（池不能无限等） |
| 上游 5xx/超时 | 把模型 base_url 指到一个会超时的地址 | 前端出现"AI 调用失败"错误气泡，**不是静默无响应** |
| 线程池打满 | 并发 400 打 SSE | 超出部分收到"系统繁忙"，而不是整站卡死 |

最后一条是这轮改造的核心验收点。

---

## 8. 常见误判

1. **闭环压测的 p95 是假的**：服务端变慢 → 客户端自动少发 → 指标好看。要开环（固定到达率）。
2. **把模型厂商当被测系统**：上游限流会伪装成"平台崩了"。
3. **忽略 SSE 完成率**：HTTP 200 但流没跑完，考生看到的就是"没有回复"——9/5 的 758→199 就是这么发生的。
4. **只看平均延迟**：考试只看 p95/p99。
5. **压测数据没清**：草稿会喂大收卷扫描，第二档结论直接失真。

---

## 9. 收尾

- 压测场次整体删除（enrollment / submission / chat_session / chat_message 分区表）；
- 恢复被改过的模型 base_url；
- 把三份 `artifacts/*.json` 和 JTL 存档，作为下一轮对比基线。
