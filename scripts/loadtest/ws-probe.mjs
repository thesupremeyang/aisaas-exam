#!/usr/bin/env node
/**
 * WebSocket 接入探针
 *
 * 目的不是测 WS 业务消息，而是测 9/5 事故那一层：
 *   「nginx 有没有把 /web/api/ws/ 正确转给 3064」+「并发握手扛不扛得住」。
 *
 * 判定：
 *   101 → 路由正确、服务正常
 *   401 → 路由正确（只是没带 token）
 *   404 → 路由缺失（9/5 就是这个问题，前端会无限重连并把日志刷爆）
 *   502/504 → 上游挂了或超时
 *
 * 用法：
 *   node ws-probe.mjs --base=http://127.0.0.1:18080/web/api --n=60 --concurrency=30
 *   node ws-probe.mjs --base=https://your.host/web/api --n=60 --concurrency=30 --token=$TOKEN
 */

import http from 'node:http'
import https from 'node:https'

const args = Object.fromEntries(
  process.argv.slice(2).map((a) => {
    const [k, v = 'true'] = a.replace(/^--/, '').split('=')
    return [k, v]
  })
)

const BASE = (args.base || process.env.EXAM_BASE || 'http://127.0.0.1:18080/web/api').replace(/\/$/, '')
const N = Number(args.n || 20)
const CONC = Number(args.concurrency || 10)
const TIMEOUT = Number(args.timeout || 15000)
const TOKEN = args.token || process.env.TOKEN || ''
const PATH = args.path || '/ws/apboa'

const url = new URL(BASE + PATH)
const lib = url.protocol === 'https:' ? https : http

function handshake() {
  return new Promise((resolve) => {
    const t0 = Date.now()
    const key = Buffer.from(String(Math.random())).toString('base64').slice(0, 24)
    const req = lib.request(
      {
        hostname: url.hostname,
        port: url.port || (url.protocol === 'https:' ? 443 : 80),
        path: url.pathname + url.search,
        method: 'GET',
        timeout: TIMEOUT,
        headers: {
          Connection: 'Upgrade',
          Upgrade: 'websocket',
          'Sec-WebSocket-Version': '13',
          'Sec-WebSocket-Key': key,
          ...(TOKEN ? { Authorization: `Bearer ${TOKEN}` } : {})
        }
      },
      (res) => {
        // 非 101：读到状态码 + 一小段响应体（用来区分「路由缺失」和「鉴权失败」）
        let body = ''
        res.setEncoding('utf8')
        res.on('data', (c) => {
          if (body.length < 512) body += c
        })
        res.on('end', () => resolve({ status: res.statusCode, ms: Date.now() - t0, body }))
        res.on('error', () => resolve({ status: res.statusCode, ms: Date.now() - t0, body }))
      }
    )
    req.on('upgrade', (res, socket) => {
      socket.destroy()
      resolve({ status: res.statusCode || 101, ms: Date.now() - t0 })
    })
    req.on('timeout', () => {
      req.destroy()
      resolve({ status: 0, ms: Date.now() - t0, err: `timeout>${TIMEOUT}ms` })
    })
    req.on('error', (e) => resolve({ status: 0, ms: Date.now() - t0, err: e.code || e.message }))
    req.end()
  })
}

function pct(sorted, p) {
  if (!sorted.length) return -1
  const i = Math.min(sorted.length - 1, Math.max(0, Math.ceil((p / 100) * sorted.length) - 1))
  return sorted[i]
}

async function main() {
  const results = []
  let cursor = 0
  async function worker() {
    while (cursor < N) {
      cursor++
      results.push(await handshake())
    }
  }
  const t0 = Date.now()
  await Promise.all(Array.from({ length: Math.min(CONC, N) }, () => worker()))
  const wall = Date.now() - t0

  const byStatus = {}
  for (const r of results) byStatus[r.status] = (byStatus[r.status] || 0) + 1
  // 500 + Spring 错误体（path=/ws/apboa）说明请求已经到达 websocket 服务，
  // 只是鉴权被包装成了 500 —— 路由是通的。
  const reachedApp500 = results.filter(
    (r) => r.status === 500 && /ws\/apboa/.test(r.body || '')
  ).length
  const ms = results.map((r) => r.ms).sort((a, b) => a - b)

  const summary = {
    target: BASE + PATH,
    n: N,
    concurrency: CONC,
    wallMs: wall,
    status: byStatus,
    reachedApp500,
    latency: { p50: pct(ms, 50), p95: pct(ms, 95), max: ms[ms.length - 1] ?? -1 },
    errors: [...new Set(results.filter((r) => r.err).map((r) => r.err))].slice(0, 5)
  }
  console.log(JSON.stringify(summary, null, 2))

  if (byStatus[404]) {
    console.error('❌ 404：nginx 路由缺失（应有 /web/api/ws/ → 127.0.0.1:3064）')
    process.exit(3)
  }
  if (reachedApp500 > 0) {
    console.warn(
      `⚠️ ${reachedApp500} 次握手返回 500（请求已到达服务）：建议把鉴权失败改成 401，避免重连风暴把日志刷爆`
    )
  }
  const ok = (byStatus[101] || 0) + (byStatus[401] || 0) + (byStatus[403] || 0) + reachedApp500
  if (ok < results.length * 0.99) {
    console.error('❌ 握手成功率 < 99%')
    process.exit(1)
  }
}

main().catch((e) => {
  console.error('FATAL:', e.message)
  process.exit(2)
})
