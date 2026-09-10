#!/usr/bin/env node

import { performance } from 'node:perf_hooks'

const apiBase = (process.env.EXAM_LOADTEST_BASE_URL || 'http://117.72.185.237:8080/web/api').replace(/\/$/, '')
const password = process.env.EXAM_LOADTEST_PASSWORD
const campaignId = process.env.EXAM_LOADTEST_CAMPAIGN_ID || '2026090100000099'
const userPrefix = process.env.EXAM_LOADTEST_USER_PREFIX || 'codex-load-20260901-'
const runId = process.env.EXAM_LOADTEST_RUN_ID || '20260901'
const count = Number(process.env.EXAM_LOADTEST_COUNT || 70)
const timeoutMs = Number(process.env.EXAM_LOADTEST_TIMEOUT_MS || 30000)

if (!password) {
  console.error('EXAM_LOADTEST_PASSWORD is required')
  process.exit(2)
}

if (!Number.isInteger(count) || count < 1 || count > 200) {
  console.error('EXAM_LOADTEST_COUNT must be an integer from 1 to 200')
  process.exit(2)
}

const users = Array.from({ length: count }, (_, index) => ({
  index: index + 1,
  username: `${userPrefix}${String(index + 1).padStart(2, '0')}`,
}))

function apiPath(path) {
  return `${apiBase}${path.startsWith('/') ? path : `/${path}`}`
}

function percentile(values, ratio) {
  if (!values.length) return null
  const sorted = [...values].sort((a, b) => a - b)
  const position = Math.min(sorted.length - 1, Math.max(0, Math.ceil(sorted.length * ratio) - 1))
  return Number(sorted[position].toFixed(1))
}

function summarize(label, results) {
  const successful = results.filter((item) => item.ok)
  const failed = results.filter((item) => !item.ok)
  const latencies = results.map((item) => item.ms).filter(Number.isFinite)
  const statusCounts = {}
  const errorSamples = []

  for (const result of results) {
    const status = result.status == null ? 'network_error' : String(result.status)
    statusCounts[status] = (statusCounts[status] || 0) + 1
    if (!result.ok && errorSamples.length < 8) {
      errorSamples.push({ index: result.index, status: result.status ?? null, error: result.error })
    }
  }

  return {
    label,
    total: results.length,
    success: successful.length,
    failed: failed.length,
    successRate: results.length ? Number(((successful.length / results.length) * 100).toFixed(2)) : 0,
    latencyMs: {
      p50: percentile(latencies, 0.5),
      p95: percentile(latencies, 0.95),
      p99: percentile(latencies, 0.99),
      max: latencies.length ? Number(Math.max(...latencies).toFixed(1)) : null,
    },
    statusCounts,
    errorSamples,
  }
}

function printSummary(summary) {
  console.log(JSON.stringify(summary))
}

async function request(label, index, method, path, token, body) {
  const started = performance.now()
  const headers = {
    Accept: 'application/json',
    'User-Agent': `codex-exam-loadtest/${runId}`,
    'X-Load-Test-Run': runId,
  }
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  try {
    const response = await fetch(apiPath(path), {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(timeoutMs),
    })
    const raw = await response.text()
    let json = null
    try {
      json = raw ? JSON.parse(raw) : null
    } catch {
      // The response body is intentionally not printed; it may contain user data.
    }
    const ms = performance.now() - started
    const ok = response.ok && json?.success === true && json?.code === 200
    return {
      label,
      index,
      ok,
      status: response.status,
      ms,
      data: json?.data,
      error: ok ? undefined : json?.msg || `HTTP ${response.status}`,
    }
  } catch (error) {
    return {
      label,
      index,
      ok: false,
      status: null,
      ms: performance.now() - started,
      error: error instanceof Error ? error.message : String(error),
    }
  }
}

async function runConcurrent(label, items, task) {
  let release
  const gate = new Promise((resolve) => {
    release = resolve
  })
  const pending = items.map(async (item) => {
    await gate
    return task(item)
  })
  release()
  const results = await Promise.all(pending)
  const summary = summarize(label, results)
  printSummary(summary)
  return { results, summary }
}

function agentPayload(user, modelConfigId, descriptionSuffix = 'create') {
  const runToken = runId.replace(/[^a-zA-Z0-9_]/g, '_')
  return {
    agentType: 'CUSTOM',
    name: `codex-load-agent-${runToken}-${String(user.index).padStart(2, '0')}`,
    agentCode: `codex_load_${runToken}_${String(user.index).padStart(2, '0')}`,
    description: `考试版并发压测合成智能体-${descriptionSuffix}`,
    modelConfigId,
    toolChoiceStrategy: 'NONE',
    followTemplate: false,
    systemPrompt: '仅用于考试版并发压测，不执行真实业务。',
    sensitiveFilterEnabled: false,
    maxIterations: 10,
    enablePlanning: false,
    showToolProcess: false,
    maxSubtasks: 10,
    requirePlanConfirmation: false,
    enableMemory: false,
    enableMemoryCompression: false,
    structuredOutputEnabled: false,
    enabled: true,
    version: '1.0.0',
    hook: [],
    subAgent: [],
    tool: [],
    mcp: [],
    mcpBindings: [],
    skill: [],
    knowledgeBase: [],
  }
}

const report = {
  startedAt: new Date().toISOString(),
  target: {
    apiBase,
    campaignId,
    runId,
    users: count,
    scope: 'exam-only',
  },
  phases: [],
}

console.log(JSON.stringify({
  event: 'load_test_start',
  target: report.target,
}))

const login = await runConcurrent('login', users, async (user) => {
  const result = await request('login', user.index, 'POST', '/auth/login', undefined, {
    username: user.username,
    password,
  })
  if (result.ok) {
    user.token = result.data?.accessToken
    user.tenantId = result.data?.userDetail?.tenantId
  }
  return result
})
report.phases.push(login.summary)

const loggedInUsers = users.filter((user) => user.token)
if (loggedInUsers.length !== users.length) {
  console.error(JSON.stringify({
    event: 'load_test_stop',
    reason: 'not_all_users_logged_in',
    loggedIn: loggedInUsers.length,
    expected: users.length,
  }))
  report.finishedAt = new Date().toISOString()
  console.log(JSON.stringify({ event: 'load_test_report', report }))
  process.exit(1)
}

const modelRead = await runConcurrent('exam_model_config_read', loggedInUsers, (user) =>
  request(
    'exam_model_config_read',
    user.index,
    'GET',
    `/exam/candidate/model-config?campaignId=${encodeURIComponent(campaignId)}`,
    user.token,
  ),
)
report.phases.push(modelRead.summary)

const modelWrite = await runConcurrent('exam_model_config_write', loggedInUsers, async (user) => {
  const result = await request(
    'exam_model_config_write',
    user.index,
    'PUT',
    `/exam/candidate/model-config?campaignId=${encodeURIComponent(campaignId)}`,
    user.token,
    {
      providerKey: 'ollama',
      schemeId: 'local',
      modelId: `codex-load-model-${runId}-${String(user.index).padStart(2, '0')}`,
      apiKey: 'codex-load-test-key',
      baseUrl: 'http://127.0.0.1:11434',
    },
  )
  if (result.ok) user.modelConfigId = result.data?.configId
  return result
})
report.phases.push(modelWrite.summary)

const modelPage = await runConcurrent('model_config_page_read', loggedInUsers, (user) =>
  request(
    'model_config_page_read',
    user.index,
    'GET',
    `/model/config/page?current=1&size=100&name=codex-load-model-${encodeURIComponent(runId)}`,
    user.token,
  ),
)
report.phases.push(modelPage.summary)

const modelDetails = await runConcurrent(
  'model_config_detail_read',
  loggedInUsers.filter((user) => user.modelConfigId),
  (user) => request('model_config_detail_read', user.index, 'GET', `/model/config/${user.modelConfigId}`, user.token),
)
report.phases.push(modelDetails.summary)

const agentPageBefore = await runConcurrent('agent_page_read_before_create', loggedInUsers, (user) =>
  request(
    'agent_page_read_before_create',
    user.index,
    'GET',
    `/agent/definition/page?current=1&size=100&name=codex-load-agent-${encodeURIComponent(runId)}`,
    user.token,
  ),
)
report.phases.push(agentPageBefore.summary)

const agentCreateUsers = loggedInUsers.filter((user) => user.modelConfigId)
const agentCreate = await runConcurrent('agent_config_create', agentCreateUsers, (user) =>
  request(
    'agent_config_create',
    user.index,
    'POST',
    '/agent/definition',
    user.token,
    agentPayload(user, user.modelConfigId),
  ),
)
report.phases.push(agentCreate.summary)

const mapToken = loggedInUsers[0]?.token
const runToken = runId.replace(/[^a-zA-Z0-9_]/g, '_')
const agentMapResult = mapToken
  ? await request(
      'agent_config_map',
      0,
      'GET',
      `/agent/definition/page?current=1&size=200&name=codex-load-agent-${encodeURIComponent(runToken)}`,
      mapToken,
    )
  : null
const agentRecords = Array.isArray(agentMapResult?.data?.records) ? agentMapResult.data.records : []
const agentByIndex = new Map()
for (const record of agentRecords) {
  const match = String(record.agentCode || '').match(new RegExp(`^codex_load_${runToken}_(\\d+)$`))
  if (match && record.id) agentByIndex.set(Number(match[1]), record)
}

const agentDetailUsers = agentCreateUsers.filter((user) => agentByIndex.has(user.index))
const agentDetails = await runConcurrent('agent_config_detail_read', agentDetailUsers, (user) =>
  request('agent_config_detail_read', user.index, 'GET', `/agent/definition/${agentByIndex.get(user.index).id}`, user.token),
)
report.phases.push(agentDetails.summary)

const agentUpdate = await runConcurrent('agent_config_update', agentDetailUsers, (user) =>
  request(
    'agent_config_update',
    user.index,
    'PUT',
    '/agent/definition',
    user.token,
    {
      ...agentPayload(user, user.modelConfigId, 'update'),
      id: agentByIndex.get(user.index).id,
    },
  ),
)
report.phases.push(agentUpdate.summary)

const cleanupAgents = agentRecords.map((record) => record.id).filter(Boolean)
const cleanupResult = mapToken && cleanupAgents.length
  ? await request('agent_config_cleanup', 0, 'DELETE', '/agent/definition', mapToken, cleanupAgents)
  : null

report.cleanup = {
  agentIdsFound: cleanupAgents.length,
  agentDelete: cleanupResult
    ? { ok: cleanupResult.ok, status: cleanupResult.status, ms: Number(cleanupResult.ms.toFixed(1)), error: cleanupResult.error }
    : { ok: false, status: null, ms: null, error: 'no agent IDs found' },
}
report.finishedAt = new Date().toISOString()
report.summary = {
  login: login.summary,
  modelConfigWrite: modelWrite.summary,
  agentCreate: agentCreate.summary,
  agentUpdate: agentUpdate.summary,
  mappedAgents: agentRecords.length,
  updatedAgents: agentDetailUsers.length,
}

console.log(JSON.stringify({ event: 'load_test_report', report }))

const required = [login.summary, modelWrite.summary, agentCreate.summary, agentUpdate.summary]
const allRequiredPassed = required.every((phase) => phase.total > 0 && phase.failed === 0)
process.exit(allRequiredPassed ? 0 : 1)
