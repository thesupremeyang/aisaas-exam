# Creator Agent Runbook

Last reconciled: 2026-07-07.

## Purpose

The marketplace creation card lets a user describe an agent in natural language, confirm one configuration summary, and then let the platform-owned creator agent create the real agent.

The flow must be predictable:

1. The user describes the requirement.
2. The assistant asks only for missing information that materially changes the result.
3. The assistant presents one concrete configuration summary and asks for confirmation.
4. After explicit confirmation, the creator agent builds the agent and ends with either `创建成功` or `创建失败`.

## Key Files

| Area | File | Responsibility |
| --- | --- | --- |
| Creator identity | `common-base/src/main/java/com/hxh/apboa/common/consts/CreatorAgentConst.java` | Platform-owned creator agent ID, code, source tenant, and header. |
| Frontend flow | `ui/src/views/Chat/index.vue` | Compact creator chat UI, confirmation actions, history panel, build progress, failure handling, timeout handling, and post-create chat jump. |
| Frontend guard | `ui/src/views/Chat/creatorStepGuard.ts` | Detects real configuration confirmation, build timeout, and created agent result metadata. |
| Frontend tests | `ui/src/views/Chat/creatorStepGuard.test.mjs` | Covers greeting, explicit confirmation, timeout behavior, and created agent parsing. |
| Prompt contract | `engine/src/main/java/com/hxh/apboa/engine/prompt/AgentSysPromptFactory.java` | Injects `CREATOR_AGENT_BUILD_CONTRACT` only for the creator agent. |
| Prompt test | `engine/src/test/java/com/hxh/apboa/engine/prompt/AgentSysPromptFactoryTest.java` | Ensures only the creator agent receives the build contract. |
| Workspace rules | `engine/src/main/java/com/hxh/apboa/engine/workspace/skills/WorkspaceSkill.java` | Tells agents to write scripts before executing multi-line Python or Node logic. |
| Builder skill | `apboa-builder/SKILL.md` | Human-readable build procedure for the creator skill. |

## Failure Fixed On 2026-07-07

Symptom: during creator agent build, the frontend progress reached 92% and stayed there.

Root cause: the creator agent could issue blocked shell commands such as `python -c` and `dir`, then guess or probe platform endpoints such as `/api/ai-agent/create`. The backend run did not reliably produce a final success or failure message, so the frontend stayed in the running build state.

Fix:

- `AgentSysPromptFactory` injects a creator-only deterministic build contract.
- The contract requires exactly one workspace script named `create_agent.py`, executed with `python create_agent.py`.
- The contract allows only documented creator endpoints and forbids endpoint guessing, probing, `/api` prefixes, `python -c`, `node -e`, `bash -c`, `dir`, and `curl`.
- The final user-facing response must be either `创建成功` with `智能体名称`, `智能体 ID`, `模型名称`, `分类`, `当前算力`, `使用技能`, and `使用入口`, or `创建失败` with a concrete reason.
- `WorkspaceSkill` no longer recommends inline interpreter commands for multi-line logic.
- The frontend unlocks the build UI on failure text and aborts with a visible timeout message after 180 seconds.

## Failure Fixed On 2026-07-08

Symptom: the compact creator UI could still reach 92% and never receive a success result after the script called `POST /agent/definition`.

Root cause: an agent with no selected optional bindings can legitimately omit empty lists such as `hook`, `subAgent`, `tool`, `skill`, or `knowledgeBase`. The backend relation services called `forEach` on those nullable lists while saving sub-items, causing errors such as `subAgentIds is null` or `toolIds is null`.

Fix:

- Optional agent binding services treat `null` and empty lists as "no bindings to save".
- `AgentOptionalBindingServiceTest` covers null optional binding IDs for hooks, sub-agents, tools, skills, and knowledge bases.
- `AgentToolServiceImplTest` preserves the direct regression for the logged `toolIds is null` failure.

## Allowed Creator Platform Endpoints

Creator build scripts should call these paths relative to `APBOA_BASE_URL`:

```text
GET /skill/page?current=1&size=1000&enabled=true
GET /model/config/page?current=1&size=1000&enabled=true
GET /prompt/template/page?current=1&size=1000&enabled=true
GET /agent/definition/page?current=1&size=1000
POST /skill/import/upload
POST /agent/definition
PUT /agent/definition
```

Do not add an `/api` prefix for this creator build flow.

## Frontend State Rules

- A greeting such as `你好` must not count as user confirmation.
- The UI enters step 2 only when the assistant message both asks for confirmation and contains enough configuration-summary fields.
- The UI enters step 3 after explicit user confirmation or while the build is locked.
- The visible progress is capped at 92% while the backend run is still active.
- While the compact creator view is in step 3, the current session is also checked through `/status`; if the backend run has ended but the SSE connection is stale, the frontend disconnects the stale stream, refreshes messages, and releases the 92% progress state.
- Failure text such as `创建失败`, `创建超时`, `执行失败`, `部署失败`, `验证失败`, `SecurityError`, or `No static resource` unlocks the progress UI.
- If the build runs longer than 180 seconds, the frontend calls `abortRun()` and saves an `error` role message.
- The compact creator history panel reuses the creator agent chat session list. Selecting a session must restore that conversation and its result card without creating a new agent.
- After success, the frontend must parse the created `智能体 ID` from the final assistant message. The success card should preserve model, category, compute, skill, and entry details, and offer a direct jump to the new agent chat page.

## Verification

Run these checks after changing the creator flow:

```bash
cd ui
node src/views/Chat/creatorStepGuard.test.mjs
pnpm exec vue-tsc --noEmit -p tsconfig.app.json
pnpm exec vite build --mode main
```

```bash
mvn -pl engine -Dtest=AgentSysPromptFactoryTest test
mvn -pl runner-runtime -am -DskipTests package
```

To smoke-test the runtime jar after packaging:

```bash
java -jar runner-runtime/target/runner-runtime-1.0-SNAPSHOT.jar
```

Run the jar from the repository root so workspace paths resolve under the repository `.apboa` directory.
