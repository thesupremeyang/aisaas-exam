# 考试门户三工作包 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付可在现有 Apboa-Next 中使用的考试门户页面、一次性 Ticket SSO 智能体桥接，以及可替换的大模型初评实现。

**Architecture:** 前端作为 Vue 3 模块访问 `/exam/**`；考试服务负责 Ticket、评测与考试数据，现有智能体平台只暴露受控的只读接口。`InitialAssessmentClient` 是初评适配边界，模型实现失败时安全退回规则实现。

**Tech Stack:** Java 21、Spring Boot 3.4、MyBatis-Plus、Vue 3、TypeScript、Ant Design Vue、Pinia。

## Global Constraints

- 所有考试数据实体继承 `BaseTenantEntity`，控制器返回 `R<T>`。
- 所有 `/exam/**` 操作先校验当前账号，再做资源归属校验。
- SSO Ticket 仅保存服务端，单次消费，5 分钟过期，`returnUrl` 必须白名单验证。
- 初评只读取脱敏智能体摘要和受控材料文本；不得执行上传代码或访问外部演示 URL。
- 新功能先写失败测试，验证失败后再实现。

---

### Task 1: 学员端与评委端 Vue 页面

**Files:**
- Create: `ui/src/views/exam/*`
- Create: `ui/src/api/exam.ts`
- Create: `ui/src/stores/exam.ts`
- Modify: `ui/src/router/*`（按现有路由组织接入）

**Interfaces:**
- Consumes: `GET/PUT/POST /exam/candidate/submissions/**`、`GET/PUT/POST /exam/reviewer/submissions/**`。
- Produces: 学员四步提交、任务轮询/进度展示、初评报告、评委复核和发布页面。

- [ ] **Step 1: 写失败的 API/状态测试**
- [ ] **Step 2: 实现 `ui/src/api/exam.ts` 与 Pinia 状态管理**
- [ ] **Step 3: 实现学员向导、AI 进度和结果页面**
- [ ] **Step 4: 实现评委复核与发布页面**
- [ ] **Step 5: 运行 `pnpm type-check` 与相关测试**

### Task 2: 一次性 Ticket SSO 与智能体只读桥接

**Files:**
- Create: `common/.../entity/ExamSsoTicket.java`
- Create: `biz/biz-assessment/.../service/ExamSsoService.java`
- Create: `runner-console/.../console/assessment/ExamSsoController.java`
- Modify: `sql/once_db_init/20260731_exam_assessment.sql`

**Interfaces:**
- Consumes: 当前账号、租户、批次、返回地址。
- Produces: `POST /exam/sso/tickets`、`POST /exam/sso/tickets/{ticket}/consume`、`GET /exam/candidate/agents`。

- [ ] **Step 1: 写 Ticket 过期、单次消费与回跳白名单失败测试**
- [ ] **Step 2: 实现 Ticket 实体、迁移、签发与消费服务**
- [ ] **Step 3: 实现考生仅可读的智能体列表及创建者归属过滤**
- [ ] **Step 4: 实现控制器及 `@PassAuth` 之外的授权约束**
- [ ] **Step 5: 运行模块测试与 `runner-console` 编译**

### Task 3: 大模型初评适配器

**Files:**
- Create: `biz/biz-assessment/.../service/RuntimeInitialAssessmentClient.java`
- Modify: `biz/biz-assessment/.../service/AssessmentTaskWorker.java`
- Modify: `runner-console/.../resources/application-*.yml`

**Interfaces:**
- Consumes: `InitialAssessmentClient.assess(String)`、批次量表、现有运行时的受控模型调用。
- Produces: 含 `dimensionScores,totalScore,confidence,conclusion,evidence` 的结构化 `InitialAssessmentResult`。

- [ ] **Step 1: 写模型 JSON 结构校验、量表越界与回退测试**
- [ ] **Step 2: 实现运行时客户端与超时/异常分类**
- [ ] **Step 3: Worker 根据批次量表选择模型客户端，失败回退规则客户端**
- [ ] **Step 4: 将模型与提示词配置放入 profile 配置，不写入数据库或 URL**
- [ ] **Step 5: 运行模块测试与管理端集成编译**

## Self Review

- 覆盖页面、SSO 只读桥接和模型初评三个确认工作包。
- 三个工作包文件边界独立；共享接口以 `/exam/**`、`InitialAssessmentClient` 明确约束。
- 不包含未确认的外部链接抓取、代码执行或多评委能力。
