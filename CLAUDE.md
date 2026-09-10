# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Apboa-Next (星图AI智能体平台) — a multi-tenant AI agent SaaS platform. Java 21 + Spring Boot 3.4.9 backend (Maven multi-module) and a Vue 3 frontend. Agents are configured in a management console and executed on a separate AI runtime that supports multiple LLM providers, RAG, MCP, tools, skills, hooks, and multi-agent orchestration.

## Build & Run

### Backend (Maven, Java 21)

Modules are layered; runners depend on `biz`/`engine`/`common` transitively, so **a root `install` is required** before running any single runner (builds the inter-module `1.0-SNAPSHOT` artifacts into the local repo).

```bash
# First time / after biz|engine|common changes — install all modules (skip tests)
mvn clean install -DskipTests

# Build just the deployable runners + their deps
mvn clean install -DskipTests -pl runner-console,runner-file,runner-runtime,runner-websocket,runner-proxy -am

# Run a single runner locally (uses the `dev` profile by default)
mvn spring-boot:run -pl runner-console
mvn spring-boot:run -pl runner-runtime
mvn spring-boot:run -pl runner-websocket
mvn spring-boot:run -pl runner-proxy
mvn spring-boot:run -pl runner-file

# Build one runner's fat jar (spring-boot:repackage is wired in each runner pom)
mvn package -pl runner-console -am -DskipTests
java -jar runner-console/target/runner-console-1.0-SNAPSHOT.jar

# Run tests / a single test
mvn test                              # all modules
mvn test -pl biz/biz-agent            # one module
mvn test -pl biz/biz-agent -Dtest=AgentDefinitionServiceImplTest  # one test class
```

The local dev profile assumes MySQL (`127.0.0.1:3306/apboa_next`, user `root`/`123456`), Redis (`127.0.0.1:6379` db `7`), and — for runtime — a vector store (pgvector at `127.0.0.1:5432/apboa_vector` by default). DB init SQL lives in `sql/once_db_init/`.

### Frontend (`ui/`, Node 20.19+ / 22.12+)

```bash
cd ui
pnpm install
pnpm dev            # dev server on :4000, proxies /api → :3060, /api/runtime → :3061, /api/ws → :3064
pnpm build          # type-check + build (main+doc bundle, zips output)
pnpm build:main     # main app only
pnpm build:doc      # doc app only
pnpm lint           # oxlint + eslint --fix
pnpm format         # prettier on src/
pnpm type-check     # vue-tsc
```

`pnpm dev` proxies `/api` → console `:3060`, `/api/runtime` → runtime `:3061`, `/api/ws` → websocket `:3064` (see [vite.config.ts](ui/vite.config.ts)). The frontend has two entry pages: `index.html` (main app) and `doc.html` (doc app), selected via `VITE_APP_TARGET`.

### Full local stack

[script/start-all.ps1](script/start-all.ps1) / [script/start-all.bat](script/start-all.bat) do `mvn install` then launch all 5 runners and the UI. (Note: the `.ps1` hard-codes a path under `Desktop\分析系统\apboa-next-master` — adjust before use, or run the runners manually.) Docker deployment (single-node `start-simple.sh` and multi-node console/execute/middleware splits) is documented in [docker/README.md](docker/README.md).

## Architecture

### Module layers (root [pom.xml](pom.xml))

```
common-base ── foundational: R<T> wrapper, UserDetail, enums, utils (TenantUtils/RedisUtils/TokenUtils), Redis pub/sub
common      ── cross-cutting: auth interceptors, MyBatis-Plus config, entities/DTOs/VOs, GlobalExceptionHandler
heartbeat   ── node heartbeat reporting (HeartbeatPayload DTO + reporter)
biz/*       ── business services + MyBatis-Plus mappers (16 domain modules)
engine      ── AI engine: agent runtime, model providers, RAG, MCP, tools, skills, hooks, memory, prompt, script sandbox, workspace
scheduler   ── job scheduling
runner-*    ── deployable Spring Boot apps (application layer — controllers live here)
ui          ── Vue 3 + Vite + Ant Design Vue + Pinia frontend
```

### The runner apps (application layer)

Each `runner-*` is a standalone Spring Boot app (`*Application.java`, `@SpringBootApplication`). **Controllers live in the runner modules**, not in `biz`. The runners assemble the relevant `biz`/`engine` modules via Maven deps; component scanning under `com.hxh.apboa.**` (and `io.agentscope.**` for runtime) picks up services, mappers, and configs transitively.

| Runner | Port | Role |
|--------|------|------|
| `runner-console` | 3060 | Management console — almost all CRUD controllers under `com.hxh.apboa.console.*` (agent, account/tenant, model, mcp, knowledge, skill, tool, hook, prompt, etc.) |
| `runner-runtime` | 3061 | AI execution runtime — drives the `engine`, exposes AG-UI streaming (`/runtime/agui`) and A2A JSON-RPC. Heartbeats to console. |
| `runner-proxy` | 3062 | Shell command execution proxy (sandboxed, time/output-limited) |
| `runner-file` | 3063 | File sync service (web disabled; heartbeat only) |
| `runner-websocket` | 3064 | WebSocket push service (decoupled from console for streaming) |

Which biz modules a runner pulls in is explicit in its `pom.xml` (e.g. `runner-console` → `biz-account`, `biz-resource`, `biz-sk`, `heartbeat`; `runner-runtime` → `scheduler`, `heartbeat`, and transitively `engine` + all its biz deps). **When adding a new domain feature, you typically add a service/mapper in the right `biz/*` module, ensure the runner that needs it depends on that biz module, and add the controller in the appropriate runner.**

### biz modules (business layer)

16 modules under [biz/](biz/): `biz-account`, `biz-agent`, `biz-a2a`, `biz-hook`, `biz-knowledge`, `biz-longterm`, `biz-mcp`, `biz-model`, `biz-params`, `biz-prompt`, `biz-resource`, `biz-sensitive`, `biz-sk`, `biz-skill`, `biz-studio`, `biz-tool`. Each follows the MyBatis-Plus pattern: `mapper/` (interfaces, XML under `src/main/java/.../mapper/**/*.xml`), `service/` + `service/impl/`. Entities and DTOs/VOs are **not** in biz — they live in `common` (see below).

### Shared data model (`common`)

- **Entities** in `common/.../entity/` extend `BaseEntity` (id via `ASSIGN_ID` snowflake, `enabled`, `createdAt/updatedAt`, `createdBy/updatedBy` — auto-filled by `MyMetaObjectHandler`) or `BaseTenantEntity` (adds `tenantId`). **Tenant-scoped tables must extend `BaseTenantEntity`.**
- **DTOs** (`common/.../dto/`) and **VOs** (`common/.../vo/`) are shared across modules.
- MyBatis-Plus global config: logic delete on `delFlag` (1=deleted, 0=active), `update-strategy: not_null`, `map-underscore-to-camel-case`. Mapper XML scanned from `classpath*:com/hxh/apboa/**/mapper/**/*.xml`.
- `common/.../mp/support/MP.java` provides paging (`MP.getPage`) and query-wrapper (`MP.getQueryWrapper`) helpers; `PageParams` is the standard paged-query input.

### Response & exception conventions

- Controllers return `R<T>` ([common-base/.../r/R.java](common-base/src/main/java/com/hxh/apboa/common/r/R.java)): `R.data(x)`, `R.success(msg)`, `R.fail(msg)`, `R.status(flag)`. Field shape: `{code, success, data, msg}`; success code is `200`.
- `GlobalExceptionHandler` ([common/.../exception/](common/src/main/java/com/hxh/apboa/common/exception/)) converts thrown exceptions to `R` responses — throw domain exceptions rather than hand-writing error responses.

### Auth & multi-tenancy

[AuthInterceptor](common/src/main/java/com/hxh/apboa/common/config/auth/AuthInterceptor.java) intercepts `/**` and supports three token types:
- **JWT** (default) — validated against Redis `login:<token>`; user info stored as `UserDetail` on the request (`SysConst.USER_DETAIL`).
- **SK token** (`sk-...` prefix) — compressed JWT for API keys; only endpoints annotated `@SkAccess` are reachable. SK validity cached in a static `SK_ID_SET`.
- **ChatKey token** — embedded chat key id; only `@ChatKeyAccess` endpoints.

Annotations on controller methods/classes: `@PassAuth` (skip auth), `@RoleNeed(TenantRole...)` (role-gated), `@SkAccess`, `@ChatKeyAccess`. Roles form a hierarchy: `TENANT_OWNER(3) > TENANT_ADMIN(2) > TENANT_EDITOR(1) > TENANT_VIEWER(0)` — user level ≥ any required level passes.

**Multi-tenancy is ThreadLocal-based**: `AuthInterceptor` sets `TenantUtils.setCurrentTenant(tenantId, tenantCode)` on each request and clears it in `postHandle`. Read the current tenant via `TenantUtils.getCurrentTenantId()`. `tenantId` is auto-filled into `BaseTenantEntity` on insert. Anything that runs outside the request thread (async/scheduled) must re-establish tenant context explicitly.

### The AI engine (`engine/`)

The execution core, used only by `runner-runtime`. Sub-packages: `agent` (runtime orchestration), `agui` (AG-UI streaming protocol), `model` (LLM provider adapters — Anthropic/OpenAI/Google + Bailian), `rag` (embedding/parser/store with pluggable vector backends: pgvector/milvus/qdrant/elasticsearch/weaviate), `mcp`, `tool` (builtins + dynamic loaders), `skill` (builtins + dynamic), `hook` (builtins + dynamic Groovy loaders), `memory` (+ `longterm`), `prompt`, `knowledge`, `security/script` (ANTLR4 + Rhino sandbox for dynamic scripts), `workspace` (+ JGit). Built on `agentscope` 1.0.12. Config knobs live under `agentscope:` and `rag:` in [runner-runtime/.../application-dev.yml](runner-runtime/src/main/resources/application-dev.yml).

### Cluster coordination

`common-base/.../cluster/` provides Redis pub/sub (`MessagePublisher`/`ChannelSubscriber`, topics in `RedisChannelTopic`) for cross-node events (e.g. SK-id sync via `SkIdSyncPublisher`/`Subscriber`). Use this for notifications that must reach all nodes, since a request may be served by console while execution happens on a remote runtime node.

## Conventions

- **Java 21** features are used throughout (records, switch expressions/patterns, sealed where applicable). Keep `sourceEncoding` UTF-8 (Chinese comments are common).
- New entity → add to `common/.../entity/`, extend `BaseEntity` or `BaseTenantEntity`. New DTO/VO → `common/.../dto|vo/`. New mapper interface + XML → the owning `biz/*` module.
- Config profiles: `dev` (local), `docker` (containerized), plus `dev-simple`/`dev.sample` variants. Runtime config is env-var driven for Docker (see [docker/README.md](docker/README.md) env reference). Sensitive values (JWT secret, DB/vector creds) come from env vars with dev defaults — don't commit real secrets.
- The `.apboa/` directory is runtime working space (workspaces, temp files) and is gitignored.
