#!/usr/bin/env node
/**
 * 智能体注册自愈验证
 *
 * 背景：平台侧新建智能体后，runtime 靠一次 Redis pub/sub 消息把 agent 注册进内存表。
 * pub/sub 是最多一次投递 —— 消息丢了，这个 agent 就会一直返回
 * `Agent not found`，直到人工 PUBLISH 或重启。真实考试里每个考生每题都会新建 agent，
 * 这条链路直接等于考生的可用性。
 *
 * 本脚本对指定 agentCode 发真实对话请求，把失败原因分类：
 *   agent_not_found → 自愈没生效（不合格）
 *   resolved        → agent 已解析（后续报模型错也算通过，说明注册这层是通的）
 *
 * 用法：
 *   node agent-heal.mjs --base=http://127.0.0.1:18080/web/api --token=$TOKEN \
 *        --sessions=artifacts/sse-sessions.json --repeat=2
 *
 *   # 只验一个 agent（需要真实 sessionId，可从 exam-sse.mjs --init 生成）
 *   node agent-heal.mjs --base=... --token=$TOKEN --thread-id=123 --agent-code=exam-candidate-xxx
 */

import { readFileSync } from 'node:fs'

const args = Object.fromEntries(
  process.argv.slice(2).map((a) => {
    const [k, v = 'true'] = a.replace(/^--/, '').split('=')
    return [k, v]
  })
)

const BASE = (args.base || process.env.EXAM_BASE || 'http://127.0.0.1:18080/web/api').replace(/\/$/, '')
const TOKEN = args.token || process.env.TOKEN || ''
const REPEAT = Number(args.repeat || 2)
const TIMEOUT = Number(args.timeout || 90000)
const QUESTION = args.question || '你好'

let pairs = []
if (args.sessions) {
  pairs = JSON.parse(readFileSync(args.sessions, 'utf8'))
} else if (args['thread-id'] && args['agent-code']) {
  pairs = [{ threadId: args['thread-id'], agentCode: args['agent-code'] }]
} else {
  console.error('需要 --sessions=file.json 或 (--thread-id + --agent-code)')
  process.exit(2)
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

function classify(text, status) {
  const t = String(text || '')
  if (/Agent not found|智能体尚未就绪|AgentNotFound/i.test(t)) return 'agent_not_found'
  if (/Agent .*not found/i.test(t)) return 'agent_not_found'
  if (status === 0) return 'network'
  // 只要不是「找不到 agent」，就说明注册这一层通了（模型配置错属于考试内容）
  return 'resolved'
}

async function probe(threadId, agentCode, round) {
  const body = {
    threadId,
    runId: `heal_${Date.now()}_${round}`,
    messages: [{ id: 'h1', role: 'user', content: QUESTION }],
    state: {},
    tools: [],
    context: [],
    forwardedProps: { agentCode }
  }
  const ctrl = new AbortController()
  const timer = setTimeout(() => ctrl.abort(), TIMEOUT)
  const t0 = Date.now()
  try {
    const res = await fetch(`${BASE}/runtime/agui/run/${encodeURIComponent(agentCode)}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...(TOKEN ? { Authorization: `Bearer ${TOKEN}` } : {})
      },
      body: JSON.stringify(body),
      signal: ctrl.signal
    })
    let text = ''
    if (res.body) {
      const reader = res.body.getReader()
      const dec = new TextDecoder()
      for (;;) {
        const { done, value } = await reader.read()
        if (done) break
        text += dec.decode(value, { stream: true })
        if (/RUN_FINISHED/i.test(text) || /"error"/.test(text)) break
      }
    } else {
      text = await res.text().catch(() => '')
    }
    return {
      agentCode,
      round,
      status: res.status,
      ms: Date.now() - t0,
      kind: classify(text, res.status),
      sample: text.replace(/\s+/g, ' ').slice(0, 200)
    }
  } catch (e) {
    return {
      agentCode,
      round,
      status: 0,
      ms: Date.now() - t0,
      kind: classify('', 0),
      sample: e?.name === 'AbortError' ? `timeout>${TIMEOUT}ms` : String(e?.message || e)
    }
  } finally {
    clearTimeout(timer)
  }
}

async function main() {
  const rows = []
  for (const p of pairs) {
    for (let r = 1; r <= REPEAT; r++) {
      const row = await probe(String(p.threadId), p.agentCode, r)
      rows.push(row)
      console.log(
        `${row.kind.padEnd(16)} status=${String(row.status).padEnd(3)} ${String(row.ms).padStart(6)}ms  ${row.agentCode}${REPEAT > 1 ? ` #${r}` : ''}`
      )
      if (row.kind !== 'resolved' && row.sample) console.log(`   ↳ ${row.sample}`)
      if (r < REPEAT) await sleep(500)
    }
  }

  const bad = rows.filter((r) => r.kind === 'agent_not_found')
  const net = rows.filter((r) => r.kind === 'network')
  console.log(
    `\n合计 ${rows.length} 次：resolved=${rows.length - bad.length - net.length} agent_not_found=${bad.length} network=${net.length}`
  )
  if (bad.length) {
    console.error('❌ 存在 Agent not found：自愈与定时对账都没有覆盖到，需要查 runtime 日志')
    process.exit(3)
  }
  if (net.length) {
    console.error('❌ 存在网络失败，先确认接入层/服务是否健康')
    process.exit(1)
  }
}

main().catch((e) => {
  console.error('FATAL:', e.message)
  process.exit(2)
})
