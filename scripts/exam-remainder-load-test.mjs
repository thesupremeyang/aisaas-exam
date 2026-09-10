#!/usr/bin/env node

import { performance } from 'node:perf_hooks'

const apiBase = (process.env.EXAM_LOADTEST_BASE_URL || 'http://117.72.185.237:8080/web/api').replace(/\/$/, '')
const password = process.env.EXAM_LOADTEST_PASSWORD
const campaignId = process.env.EXAM_LOADTEST_CAMPAIGN_ID || '2026090200000099'
const userPrefix = process.env.EXAM_LOADTEST_USER_PREFIX || 'codex-rest-20260902-'
const runId = process.env.EXAM_LOADTEST_RUN_ID || '20260902'
const count = Number(process.env.EXAM_LOADTEST_COUNT || 70)
const timeoutMs = Number(process.env.EXAM_LOADTEST_TIMEOUT_MS || 90000)
const modelBaseUrl = process.env.EXAM_LOADTEST_MODEL_BASE_URL || 'http://127.0.0.1:18080/v1'

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

const report = {
  startedAt: new Date().toISOString(),
  target: {
    apiBase,
    runtimeBase: `${apiBase}/runtime`,
    campaignId,
    runId,
    users: count,
    scope: 'exam-only',
    model: 'loopback-only-openai-compatible-mock',
  },
  phases: [],
  state: {
    tenantId: '2098998000000000000',
    campaignId,
    userPrefix,
    agentCodePrefix: `codex_rest_${runId}_`,
    attachmentNamePrefix: `codex-rest-${runId}-`,
  },
}

function apiPath(path) {
  return `${apiBase}${path.startsWith('/') ? path : `/${path}`}`
}

function percentile(values, ratio) {
  if (!values.length) return null
  const sorted = [...values].sort((a, b) => a - b)
  const position = Math.min(sorted.length - 1, Math.max(0, Math.ceil(sorted.length * ratio) - 1))
  return Number(sorted[position].toFixed(1))
}

function latencySummary(values) {
  return {
    p50: percentile(values, 0.5),
    p95: percentile(values, 0.95),
    p99: percentile(values, 0.99),
    max: values.length ? Number(Math.max(...values).toFixed(1)) : null,
  }
}

function summarize(label, results, extra = {}) {
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
    latencyMs: latencySummary(latencies),
    statusCounts,
    errorSamples,
    ...extra,
  }
}

function summarizeStreams(label, results) {
  const summary = summarize(label, results)
  const firstByte = results.map((item) => item.firstByteMs).filter(Number.isFinite)
  const firstEvent = results.map((item) => item.firstEventMs).filter(Number.isFinite)
  const totalEvents = results.reduce((sum, item) => sum + (item.eventCount || 0), 0)
  const totalTextChunks = results.reduce((sum, item) => sum + (item.textChunks || 0), 0)
  const rawErrors = results.reduce((sum, item) => sum + (item.rawErrors || 0), 0)
  return {
    ...summary,
    firstByteMs: latencySummary(firstByte),
    firstEventMs: latencySummary(firstEvent),
    eventCount: totalEvents,
    textChunkCount: totalTextChunks,
    rawErrorCount: rawErrors,
  }
}

function printPhase(summary) {
  console.log(JSON.stringify({ event: 'phase_summary', summary }))
}

async function requestJson(label, index, method, path, token, body) {
  const started = performance.now()
  const headers = {
    Accept: 'application/json',
    'User-Agent': `codex-exam-remainder-test/${runId}`,
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
      // Keep response bodies out of the load-test report.
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

async function uploadFile(user) {
  const started = performance.now()
  const fileName = `codex-rest-${runId}-${String(user.index).padStart(2, '0')}.txt`
  const content = `codex isolated exam upload ${runId} user ${user.index}\n`.repeat(32)
  const form = new FormData()
  form.append('file', new Blob([content], { type: 'text/plain' }), fileName)
  try {
    const response = await fetch(apiPath('/attach/upload'), {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${user.token}`,
        'User-Agent': `codex-exam-remainder-test/${runId}`,
        'X-Load-Test-Run': runId,
      },
      body: form,
      signal: AbortSignal.timeout(timeoutMs),
    })
    const raw = await response.text()
    let json = null
    try {
      json = raw ? JSON.parse(raw) : null
    } catch {
      // Keep response bodies out of the load-test report.
    }
    const ms = performance.now() - started
    const ok = response.ok && json?.success === true && json?.code === 200 && json?.data
    return {
      label: 'file_upload',
      index: user.index,
      ok,
      status: response.status,
      ms,
      attachId: ok ? String(json.data) : undefined,
      fileName,
      expectedBytes: Buffer.byteLength(content),
      error: ok ? undefined : json?.msg || `HTTP ${response.status}`,
    }
  } catch (error) {
    return {
      label: 'file_upload',
      index: user.index,
      ok: false,
      status: null,
      ms: performance.now() - started,
      fileName,
      error: error instanceof Error ? error.message : String(error),
    }
  }
}

async function downloadFile(user, attachId, expectedBytes) {
  const started = performance.now()
  try {
    const response = await fetch(apiPath(`/attach/download/${encodeURIComponent(attachId)}`), {
      headers: {
        Accept: 'application/octet-stream',
        Authorization: `Bearer ${user.token}`,
        'User-Agent': `codex-exam-remainder-test/${runId}`,
      },
      signal: AbortSignal.timeout(timeoutMs),
    })
    const bytes = new Uint8Array(await response.arrayBuffer())
    const ms = performance.now() - started
    const ok = response.ok && bytes.byteLength === expectedBytes
    return {
      label: 'file_download_verify',
      index: user.index,
      ok,
      status: response.status,
      ms,
      bytes: bytes.byteLength,
      expectedBytes,
      error: ok ? undefined : `expected ${expectedBytes} bytes, received ${bytes.byteLength}`,
    }
  } catch (error) {
    return {
      label: 'file_download_verify',
      index: user.index,
      ok: false,
      status: null,
      ms: performance.now() - started,
      error: error instanceof Error ? error.message : String(error),
    }
  }
}

async function runAgui(user, mode) {
  const started = performance.now()
  const url = `${apiBase}/runtime/agui/run/${encodeURIComponent(user.agentCode)}`
  const body = {
    threadId: String(user.sessionId),
    runId: `codex_${runId}_${mode}_${String(user.index).padStart(2, '0')}`,
    messages: [{
      id: `codex-user-${runId}-${mode}-${user.index}`,
      role: 'user',
      content: `请返回一段简短的测试答复，编号 ${user.index}。`,
    }],
    tools: [],
    context: [],
    state: {},
    forwardedProps: {
      agentId: String(user.agentId),
      agentCode: user.agentCode,
      fileIds: [],
      memoryActive: false,
      planActive: false,
      toolProcessActive: false,
    },
  }
  let response
  try {
    response = await fetch(url, {
      method: 'POST',
      headers: {
        Accept: 'text/event-stream',
        'Content-Type': 'application/json',
        Authorization: `Bearer ${user.token}`,
        'User-Agent': `codex-exam-remainder-test/${runId}`,
        'X-Load-Test-Run': runId,
      },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(timeoutMs),
    })
  } catch (error) {
    return {
      label: `ai_chat_${mode}`,
      index: user.index,
      ok: false,
      status: null,
      ms: performance.now() - started,
      firstByteMs: null,
      firstEventMs: null,
      eventCount: 0,
      textChunks: 0,
      rawErrors: 0,
      error: error instanceof Error ? error.message : String(error),
    }
  }

  const firstByteMs = performance.now() - started
  if (!response.ok || !response.body) {
    const raw = await response.text()
    return {
      label: `ai_chat_${mode}`,
      index: user.index,
      ok: false,
      status: response.status,
      ms: performance.now() - started,
      firstByteMs,
      firstEventMs: null,
      eventCount: 0,
      textChunks: 0,
      rawErrors: 0,
      error: raw.slice(0, 160) || `HTTP ${response.status}`,
    }
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let firstEventMs = null
  let eventCount = 0
  let textChunks = 0
  let rawErrors = 0
  let runFinished = false
  let runStarted = false

  const processLine = (line) => {
    if (!line.startsWith('data:')) return
    const data = line.replace(/^data:\s*/, '').trim()
    if (!data || data === '[DONE]') return
    let event
    try {
      event = JSON.parse(data)
      if (typeof event === 'string') event = JSON.parse(event.trim())
    } catch {
      return
    }
    if (firstEventMs == null) firstEventMs = performance.now() - started
    eventCount += 1
    if (event.type === 'RUN_STARTED') runStarted = true
    if (event.type === 'RUN_FINISHED') runFinished = true
    if (event.type === 'TEXT_MESSAGE_CONTENT' || event.type === 'TEXT_MESSAGE_CHUNK') textChunks += 1
    if (event.type === 'RAW' && event.rawEvent?.error) rawErrors += 1
  }

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        buffer += decoder.decode()
        break
      }
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split(/\r?\n/)
      buffer = lines.pop() || ''
      for (const line of lines) processLine(line)
    }
    if (buffer) processLine(buffer)
  } finally {
    reader.releaseLock()
  }

  const ms = performance.now() - started
  const ok = response.ok && runStarted && runFinished && rawErrors === 0
  return {
    label: `ai_chat_${mode}`,
    index: user.index,
    ok,
    status: response.status,
    ms,
    firstByteMs,
    firstEventMs,
    eventCount,
    textChunks,
    rawErrors,
    error: ok ? undefined : `runStarted=${runStarted}, runFinished=${runFinished}, rawErrors=${rawErrors}`,
  }
}

async function runConcurrent(label, items, task, stream = false) {
  let release
  const gate = new Promise((resolve) => { release = resolve })
  const pending = items.map(async (item) => {
    await gate
    return task(item)
  })
  release()
  const results = await Promise.all(pending)
  const summary = stream ? summarizeStreams(label, results) : summarize(label, results)
  printPhase(summary)
  report.phases.push(summary)
  return { results, summary }
}

function agentPayload(user, modelConfigId, streaming) {
  const payload = {
    agentType: 'CUSTOM',
    name: `codex-rest-agent-${runId}-${String(user.index).padStart(2, '0')}`,
    agentCode: `codex_rest_${runId}_${String(user.index).padStart(2, '0')}`,
    description: `考试版剩余链路隔离压测-${streaming ? 'stream' : 'nonstream'}`,
    modelConfigId,
    modelParamsOverride: { streaming },
    toolChoiceStrategy: 'NONE',
    followTemplate: false,
    systemPrompt: '仅用于考试版隔离压力测试，不执行真实业务。',
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
  return payload
}

function requireAll(summary, label) {
  if (summary.failed > 0) throw new Error(`${label} failed: ${summary.failed}/${summary.total}`)
}

try {
  console.log(JSON.stringify({ event: 'load_test_start', target: report.target }))

  const login = await runConcurrent('login', users, async (user) => {
    const result = await requestJson('login', user.index, 'POST', '/auth/login', undefined, {
      username: user.username,
      password,
    })
    if (result.ok) {
      user.token = result.data?.accessToken
      user.tenantId = result.data?.userDetail?.tenantId
    }
    return result
  })
  requireAll(login.summary, 'login')

  const modelWrite = await runConcurrent('exam_model_config_write_custom_mock', users, async (user) => {
    const result = await requestJson(
      'exam_model_config_write_custom_mock',
      user.index,
      'PUT',
      `/exam/candidate/model-config?campaignId=${encodeURIComponent(campaignId)}`,
      user.token,
      {
        providerKey: 'custom',
        schemeId: 'custom',
        modelId: 'codex-rest-model',
        apiKey: 'codex-rest-loopback-key',
        baseUrl: modelBaseUrl,
      },
    )
    if (result.ok) user.modelConfigId = result.data?.configId
    return result
  })
  requireAll(modelWrite.summary, 'model config')

  const agentCreate = await runConcurrent('agent_config_create_nonstream', users, (user) =>
    requestJson(
      'agent_config_create_nonstream',
      user.index,
      'POST',
      '/agent/definition',
      user.token,
      agentPayload(user, user.modelConfigId, false),
    ),
  )
  requireAll(agentCreate.summary, 'agent create')

  const mapResult = await requestJson(
    'agent_config_map',
    0,
    'GET',
    `/agent/definition/page?current=1&size=200&name=${encodeURIComponent(`codex-rest-agent-${runId}`)}`,
    users[0].token,
  )
  const records = Array.isArray(mapResult.data?.records) ? mapResult.data.records : []
  const byIndex = new Map()
  for (const record of records) {
    const match = String(record.agentCode || '').match(new RegExp(`^codex_rest_${runId}_(\\d+)$`))
    if (match && record.id) byIndex.set(Number(match[1]), record)
  }
  report.state.agentIds = [...byIndex.values()].map((record) => String(record.id))
  if (byIndex.size !== count) throw new Error(`agent mapping incomplete: ${byIndex.size}/${count}`)
  for (const user of users) {
    const record = byIndex.get(user.index)
    user.agentId = record.id
    user.agentCode = record.agentCode
  }

  const sessions = await runConcurrent('chat_session_create', users, async (user) => {
    const result = await requestJson(
      'chat_session_create',
      user.index,
      'POST',
      '/agent/chat/session',
      user.token,
      { agentId: user.agentId, title: `Codex isolated remainder ${user.index}` },
    )
    if (result.ok) user.sessionId = result.data?.id
    return result
  })
  requireAll(sessions.summary, 'chat session create')
  report.state.sessionIds = users.map((user) => String(user.sessionId))

  const upload = await runConcurrent('file_upload_70_concurrent', users, uploadFile)
  for (const result of upload.results) {
    if (result.ok) {
      const user = users[result.index - 1]
      user.attachId = result.attachId
      user.expectedBytes = result.expectedBytes
    }
  }
  report.state.attachIds = users.filter((user) => user.attachId).map((user) => String(user.attachId))

  const uploadUsers = users.filter((user) => user.attachId)
  const download = await runConcurrent(
    'file_download_verify',
    uploadUsers,
    (user) => downloadFile(user, user.attachId, user.expectedBytes),
  )

  const smoke = await runAgui(users[0], 'nonstream_smoke')
  const smokeSummary = summarizeStreams('ai_chat_nonstream_smoke', [smoke])
  printPhase(smokeSummary)
  report.phases.push(smokeSummary)
  if (!smoke.ok) throw new Error('single-user AG-UI smoke test failed')

  const nonstream = await runConcurrent('ai_chat_nonstream_70_concurrent', users, (user) => runAgui(user, 'nonstream'), true)
  requireAll(nonstream.summary, 'nonstream AI chat')

  const streamUpdate = await runConcurrent('agent_config_update_streaming', users, (user) =>
    requestJson(
      'agent_config_update_streaming',
      user.index,
      'PUT',
      '/agent/definition',
      user.token,
      { ...agentPayload(user, user.modelConfigId, true), id: user.agentId },
    ),
  )
  requireAll(streamUpdate.summary, 'agent streaming update')

  const stream = await runConcurrent('ai_chat_stream_70_concurrent', users, (user) => runAgui(user, 'stream'), true)
  requireAll(stream.summary, 'streaming AI chat')

  report.finishedAt = new Date().toISOString()
  report.summary = {
    upload: upload.summary,
    download: download.summary,
    nonstream: nonstream.summary,
    streaming: stream.summary,
  }
  console.log(JSON.stringify({ event: 'load_test_report', report }))
  process.exit(0)
} catch (error) {
  report.finishedAt = new Date().toISOString()
  report.error = error instanceof Error ? error.message : String(error)
  console.log(JSON.stringify({ event: 'load_test_report', report }))
  process.exit(1)
}
