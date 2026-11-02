#!/usr/bin/env node
/**
 * 智能体对话（AG-UI / SSE）并发压测
 *
 * 为什么单独一个脚本：通用 HTTP 压测只能测「发出去、收回响应」，
 * 而考场真正的瓶颈是「一条 SSE 长连接占用多久、首个 token 多久到、流有没有正常收尾」。
 * 9/5 那次 758 条提问只回了 199 条，用 HTTP 200 计数是看不出问题的。
 *
 * 用法：
 *   # 0) 先拿一个考生 token（浏览器 F12 里的 Authorization，去掉 Bearer 前缀）
 *   export TOKEN=xxxxx
 *
 *   # 1) 造 40 个会话（每个会话绑定同一个 agentCode）
 *   node exam-sse.mjs --base=http://127.0.0.1:18080/web/api --token=$TOKEN \
 *        --agent-code=exam-candidate-xxxx --init=40 --out=artifacts/sse-sessions.json
 *
 *   # 2) 用这 40 个会话压测，每人 3 轮
 *   node exam-sse.mjs --base=http://127.0.0.1:18080/web/api --token=$TOKEN \
 *        --sessions=artifacts/sse-sessions.json --vus=40 --rounds=3 \
 *        --summary=artifacts/sse-summary.json
 *
 * 输出：JMeter 同格式 .jtl（--jtl）+ 汇总 JSON（--summary）
 */

import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'

const args = Object.fromEntries(
  process.argv.slice(2).map((a) => {
    const [k, v = 'true'] = a.replace(/^--/, '').split('=')
    return [k, v]
  })
)

const BASE = (args.base || process.env.EXAM_BASE || 'http://127.0.0.1:18080/web/api').replace(/\/$/, '')
const TOKEN = args.token || process.env.TOKEN || ''
const AGENT_CODE = args['agent-code'] || process.env.AGENT_CODE || ''
const VUS = Number(args.vus || 10)
const ROUNDS = Number(args.rounds || 1)
const INIT = Number(args.init || 0)
const THINK_MS = Number(args['think-ms'] || 0)
const TIMEOUT_MS = Number(args.timeout || 120000)
const QUESTION =
  args.question ||
  process.env.QUESTION ||
  '请用三句话说明你能帮考生完成什么工作。'
const JTL = args.jtl || ''
const SUMMARY = args.summary || ''

const OUT_DIR = join(process.cwd(), 'artifacts')
mkdirSync(OUT_DIR, { recursive: true })

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const now = () => Date.now()

function authHeaders() {
  const h = { 'Content-Type': 'application/json' }
  if (TOKEN) h.Authorization = `Bearer ${TOKEN}`
  return h
}

async function createSession(agentCode, title) {
  const res = await fetch(`${BASE}/agent/chat/session`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ agentId: agentCode, title })
  })
  const text = await res.text()
  let json
  try {
    json = JSON.parse(text)
  } catch {
    throw new Error(`create session 非 JSON 响应: ${res.status} ${text.slice(0, 200)}`)
  }
  const id = json?.data?.id || json?.data
  if (!id) throw new Error(`create session 失败: ${JSON.stringify(json).slice(0, 300)}`)
  return String(id)
}

/**
 * 跑一轮对话，返回度量。
 * 关键指标：status（HTTP 码）、ttft（首个文本增量）、ttfb（首字节）、
 *           finished（收到 RUN_FINISHED）、err（服务端 error 事件）、bytes。
 */
async function runOnce(threadId, agentCode, round) {
  const runId = `lt_${now()}_${Math.random().toString(36).slice(2, 9)}`
  const body = {
    threadId,
    runId,
    messages: [{ id: `m_${round}`, role: 'user', content: QUESTION }],
    state: {},
    tools: [],
    context: [],
    forwardedProps: { agentCode }
  }

  const t0 = now()
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS)

  const m = {
    label: 'sse-run',
    start: t0,
    status: 0,
    ttfb: -1,
    ttft: -1,
    elapsed: -1,
    bytes: 0,
    events: 0,
    finished: false,
    err: '',
    ok: false
  }

  try {
    const res = await fetch(`${BASE}/runtime/agui/run/${encodeURIComponent(agentCode)}`, {
      method: 'POST',
      headers: { ...authHeaders(), Accept: 'text/event-stream' },
      body: JSON.stringify(body),
      signal: controller.signal
    })
    m.status = res.status
    m.ttfb = now() - t0

    if (!res.ok || !res.body) {
      m.err = `HTTP ${res.status} ${(await res.text().catch(() => '')).slice(0, 160)}`
      m.elapsed = now() - t0
      return m
    }

    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buf = ''

    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      m.bytes += value.byteLength
      buf += decoder.decode(value, { stream: true })

      let idx
      while ((idx = buf.indexOf('\n\n')) >= 0) {
        const chunk = buf.slice(0, idx)
        buf = buf.slice(idx + 2)
        for (const line of chunk.split('\n')) {
          const raw = line.startsWith('data:') ? line.slice(5).trim() : ''
          if (!raw || raw === '[DONE]') continue
          m.events++
          let evt
          try {
            evt = JSON.parse(raw)
          } catch {
            continue
          }
          // 兼容两种包装：事件对象本身，或 {type, data}
          const inner = evt.data && typeof evt.data === 'object' ? evt.data : evt
          const type = String(evt.type || inner.type || '')

          if (m.ttft < 0 && /TEXT_MESSAGE_CONTENT|TEXT_MESSAGE_CHUNK/i.test(type)) {
            m.ttft = now() - t0
          }
          if (/RUN_FINISHED|RUN_COMPLETED/i.test(type)) m.finished = true
          const errorText = evt.error || inner.error
          if (errorText) m.err = String(errorText).slice(0, 200)
        }
      }
      if (m.finished) break
    }

    m.elapsed = now() - t0
    m.ok = res.status === 200 && m.finished && !m.err
    if (!m.err && !m.finished) m.err = 'stream ended without RUN_FINISHED'
    return m
  } catch (e) {
    m.elapsed = now() - t0
    m.err = e?.name === 'AbortError' ? `timeout>${TIMEOUT_MS}ms` : String(e?.message || e)
    return m
  } finally {
    clearTimeout(timer)
  }
}

function pct(sorted, p) {
  if (!sorted.length) return -1
  const i = Math.min(sorted.length - 1, Math.max(0, Math.ceil((p / 100) * sorted.length) - 1))
  return sorted[i]
}

function summarize(rows) {
  const el = rows.map((r) => r.elapsed).filter((v) => v >= 0).sort((a, b) => a - b)
  const tf = rows.map((r) => r.ttft).filter((v) => v >= 0).sort((a, b) => a - b)
  const err = rows.filter((r) => !r.ok)
  const byStatus = {}
  for (const r of rows) byStatus[r.status] = (byStatus[r.status] || 0) + 1
  return {
    total: rows.length,
    ok: rows.length - err.length,
    errorRate: rows.length ? +(err.length / rows.length).toFixed(4) : 0,
    finishedRate: rows.length ? +(rows.filter((r) => r.finished).length / rows.length).toFixed(4) : 0,
    status: byStatus,
    elapsed: { p50: pct(el, 50), p95: pct(el, 95), p99: pct(el, 99), max: el[el.length - 1] ?? -1 },
    ttft: { p50: pct(tf, 50), p95: pct(tf, 95), p99: pct(tf, 99) },
    errorSamples: [...new Set(err.map((r) => r.err))].slice(0, 8)
  }
}

async function main() {
  if (!TOKEN) throw new Error('缺少 --token（或环境变量 TOKEN）')

  // 模式一：造会话
  if (INIT > 0) {
    if (!AGENT_CODE) throw new Error('--init 模式必须给 --agent-code')
    const sessions = []
    for (let i = 0; i < INIT; i++) {
      const threadId = await createSession(AGENT_CODE, `lt-${now()}-${i}`)
      sessions.push({ threadId, agentCode: AGENT_CODE })
      if ((i + 1) % 10 === 0) console.log(`created ${i + 1}/${INIT}`)
    }
    const out = args.out || join(OUT_DIR, `sse-sessions-${now()}.json`)
    writeFileSync(out, JSON.stringify(sessions, null, 2))
    console.log(`会话已写入 ${out}（${sessions.length} 个）`)
    return
  }

  // 模式二：压测
  let sessions
  if (args.sessions) {
    sessions = JSON.parse(readFileSync(args.sessions, 'utf8'))
  } else if (args['thread-id'] && AGENT_CODE) {
    sessions = [{ threadId: args['thread-id'], agentCode: AGENT_CODE }]
  } else {
    throw new Error('需要 --sessions=file.json 或 (--thread-id + --agent-code)')
  }
  if (!sessions.length) throw new Error('会话列表为空')

  const rows = []
  const workers = Math.min(VUS, sessions.length)
  let cursor = 0

  async function worker(id) {
    for (let r = 0; r < ROUNDS; r++) {
      const s = sessions[cursor++ % sessions.length]
      const m = await runOnce(s.threadId, s.agentCode, r)
      m.vu = id
      rows.push(m)
      if (THINK_MS > 0) await sleep(THINK_MS)
    }
  }

  const started = now()
  await Promise.all(Array.from({ length: workers }, (_, i) => worker(i)))
  const wall = now() - started

  const summary = summarize(rows)
  summary.wallMs = wall
  summary.vus = workers
  summary.rounds = ROUNDS
  summary.rps = +(rows.length / (wall / 1000)).toFixed(2)

  console.log(JSON.stringify(summary, null, 2))

  if (JTL) {
    const lines = rows.map(
      (r) =>
        `${r.start},${Math.max(r.elapsed, 0)},${r.label},${r.status || 'ERR'},${r.ok},${r.err ? 'err' : 'ok'},0,0,${r.bytes},${r.ttft},${r.finished ? 1 : 0},vu-${r.vu}`
    )
    writeFileSync(
      JTL,
      'timeStamp,elapsed,label,responseCode,success,responseMessage,threads,grpThreads,bytes,latency,finished,threadName\n' +
        lines.join('\n') +
        '\n'
    )
    console.log(`jtl -> ${JTL}`)
  }
  if (SUMMARY) {
    writeFileSync(SUMMARY, JSON.stringify(summary, null, 2))
    console.log(`summary -> ${SUMMARY}`)
  }

  // 断言式退出码：错误率 > 1% 或完成率 < 99% 视为不通过
  const failed = summary.errorRate > 0.01 || summary.finishedRate < 0.99
  process.exit(failed ? 1 : 0)
}

main().catch((e) => {
  console.error('FATAL:', e.message)
  process.exit(2)
})
