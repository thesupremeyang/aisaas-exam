#!/usr/bin/env node
/**
 * 实操考试系统并发压测脚本
 *
 * 严格对齐理论考试系统 LOADTEST-METHOD.md §4 的六个关键设计：
 *   4.1 集合点       —— 不加集合点 = 靠启动顺序自动错峰，测出来的数字是假的
 *   4.2 If 守卫      —— 上游失败后必须跳过下游，否则制造大量「假 500」
 *   4.3 标签         —— 每个采样都要有 label，否则无法按接口分组
 *   4.4 复现重试     —— 本系统开考前会拒绝，需按服务端给的时机重进
 *   4.5 开考对齐集合点 —— NO_OPEN 建场，集合点释放那一刻才开考
 *   4.6 对照组       —— 退避做成参数，对照档置 0（--fallback=0）
 *
 * 被测链路（对应方法论 §2 的 S1/S2/S3）：
 *   S1 enter  POST /exam/candidate/submissions/enrollments/{enrollmentId}/submissions
 *   S2 draft  PUT  /exam/candidate/submissions/{id}/draft
 *   S3 submit POST /exam/candidate/submissions/{id}/submit
 *
 * 输出 JMeter 同格式 .jtl，可直接用 analyze.py 解析。
 *
 * 用法：
 *   node exam-lt.mjs --users=100 --tag=100
 */
import { readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = dirname(fileURLToPath(import.meta.url))
const OUT = join(HERE, 'artifacts')

const args = Object.fromEntries(
  process.argv.slice(2).map((a) => {
    const [k, v = 'true'] = a.replace(/^--/, '').split('=')
    return [k, v]
  })
)

const USERS = Number(args.users || 100)
const DRAFTS = Number(args.drafts || 2)      // 自动保存次数（S2）
const THINK = Number(args.think || 800)      // 答题间隔 ms
const FALLBACK = Number(args.fallback || 0)  // §4.6：对照档必须为 0
const TAG = args.tag || String(USERS)
const JTL = join(OUT, `run-${TAG}.jtl`)

const exam = JSON.parse(readFileSync(join(OUT, 'exam.json'), 'utf8'))
const all = JSON.parse(readFileSync(join(OUT, 'candidates.json'), 'utf8'))
const users = all.slice(0, USERS)
if (users.length < USERS) throw new Error(`名单只有 ${all.length} 人，不够 ${USERS}`)

const BASE = exam.base
const rows = []
const lock = () => {} // rows.push 在单线程内是安全的

/** 成功 = HTTP 2xx 且业务 code 为 200。系统用 200 + code:500 表示业务失败，必须看 code。 */
async function sample(label, method, path, { token, body, thread } = {}) {
  const url = BASE + path
  const started = Date.now()
  let elapsed = 0
  let httpCode = 0
  let bizCode = '0'
  let msg = ''
  let json = {}
  try {
    const res = await fetch(url, {
      method,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      // 注意：系统 id 是 18 位 bigint，超过 JS Number 的 2^53。
      // 经 Number() 或 JSON.stringify 中转会丢精度，导致 agentId 对不上。
      // 所以涉及 id 的请求体一律用字符串模板直接写字面量。
      body: body === undefined ? undefined : (typeof body === 'string' ? body : JSON.stringify(body)),
      signal: AbortSignal.timeout(60000),
    })
    httpCode = res.status
    json = await res.json().catch(() => ({}))
    bizCode = String(json?.code ?? httpCode)
    msg = String(json?.msg ?? '').slice(0, 160)
  } catch (e) {
    bizCode = 'EXC'
    msg = String(e?.message || e).slice(0, 160)
  } finally {
    elapsed = Date.now() - started
  }
  const ok = httpCode >= 200 && httpCode < 300 && bizCode === '200'
  rows.push([
    started, elapsed, label, bizCode, msg.replace(/[\r\n,]/g, ' '),
    thread || 'tg', ok ? 'true' : 'false', '0', url,
  ])
  return { ok, json, bizCode, msg }
}

/** 集合点：全员到齐才一起放；带超时兜底，避免个别线程异常导致整体挂死。 */
class Barrier {
  constructor(n) { this.n = n; this.count = 0; this.waiters = [] }
  arrive(timeoutMs = 120000) {
    return new Promise((resolve) => {
      if (++this.count >= this.n) {
        const w = this.waiters
        this.waiters = []
        w.forEach((fn) => fn())
        return resolve(true)
      }
      const timer = setTimeout(() => resolve(false), timeoutMs)
      this.waiters.push(() => { clearTimeout(timer); resolve(true) })
    })
  }
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function main() {
  console.log(`压测开始：${USERS} 名考生  草稿 ${DRAFTS} 次  答题间隔 ${THINK}ms  fallback=${FALLBACK}ms`)
  console.log(`  campaignId=${exam.campaignId}  已开考=${exam.opened}`)

  // ---- Phase 0：登录（错峰完成，模拟考生提前入场候考；不计入开考尖峰）----
  const LOGIN_CONC = 50
  const tokens = new Map()
  for (let i = 0; i < users.length; i += LOGIN_CONC) {
    await Promise.all(users.slice(i, i + LOGIN_CONC).map(async (u) => {
      const r = await sample('login', 'POST', '/auth/login', {
        body: { username: u.username, password: u.password },
        thread: u.username,
      })
      const t = r.json?.data?.accessToken
      if (t) tokens.set(u.username, t)
    }))
  }
  const loginOk = tokens.size
  console.log(`  Phase0 登录成功 ${loginOk}/${users.length}`)
  if (loginOk === 0) throw new Error('全员登录失败，检查账号与后端状态')

  // ---- Barrier 1：开考集合点 ----
  const b1 = new Barrier(users.length)
  const b2 = new Barrier(users.length)

  const runOne = async (u, idx) => {
    const token = tokens.get(u.username)
    const th = u.username
    if (!token) return // §4.2 If 守卫：登录失败就不往下打

    await b1.arrive()

    // §4.5：0 号线程在集合点释放那一刻开考
    if (idx === 0 && !exam.opened) {
      await sample('open', 'POST', `/exam/admin/campaigns/${exam.campaignId}/start-now`, {
        token: adminToken, thread: th,
      })
    }
    // 等开考状态落库，否则先醒的线程会看到未开考
    await sleep(2000)

    // ---- S1 enter：全员同一瞬间进入考试 ----
    const enterBody = `{"questionId":${exam.questionId}}`
    let enter = await sample('enter', 'POST',
      `/exam/candidate/submissions/enrollments/${u.enrollmentId}/submissions`,
      { token, body: enterBody, thread: th })

    // §4.4 忠实复现客户端重试：开考瞬间未就绪时按服务端提示重进
    let retry = 0
    while (!enter.ok && retry < 3) {
      retry++
      if (FALLBACK > 0) await sleep(Math.random() * FALLBACK)
      await sleep(500)
      enter = await sample('enter-retry', 'POST',
        `/exam/candidate/submissions/enrollments/${u.enrollmentId}/submissions`,
        { token, body: enterBody, thread: th })
    }
    if (!enter.ok) return // §4.2 If 守卫

    const submissionId = String(enter.json?.data?.id)

    // ---- S2 draft：答题中的自动保存 ----
    for (let d = 0; d < DRAFTS; d++) {
      await sleep(THINK)
      const answer = `压测作答 ${u.username} 第${d + 1}次保存`
      const draftBody =
        `{"agentId":${u.agentId},"workType":"AGENT","currentStep":${d + 1},` +
        `"formJson":${JSON.stringify(JSON.stringify({ answer }))}}`
      await sample('draft', 'PUT', `/exam/candidate/submissions/${submissionId}/draft`, {
        token, body: draftBody, thread: th,
      })
    }

    // ---- Barrier 2：交卷集合点 ----
    await b2.arrive()

    // ---- S3 submit：全员同一瞬间交卷 ----
    await sample('submit', 'POST', `/exam/candidate/submissions/${submissionId}/submit`, {
      token, thread: th,
    })
  }

  // 管理员 token（开考用）
  const adminLogin = await fetch(BASE + '/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: '277fc0217db5d364b3b886a9672ea9d3' }),
  })
  const adminToken = (await adminLogin.json())?.data?.accessToken

  const t0 = Date.now()
  await Promise.all(users.map((u, i) => runOne(u, i).catch(() => {})))
  const total = Date.now() - t0

  // ---- 落 JTL（每次覆盖，避免 §8 的「追加不覆盖」坑）----
  const header = 'timeStamp,elapsed,label,responseCode,responseMessage,threadName,success,bytes,URL'
  writeFileSync(JTL, header + '\n' + rows.map((r) => r.join(',')).join('\n') + '\n')

  const fail = rows.filter((r) => r[6] === 'false').length
  console.log(`\n完成：${rows.length} 个样本  失败 ${fail} (${(fail / rows.length * 100).toFixed(2)}%)  耗时 ${(total / 1000).toFixed(1)}s`)
  console.log(`  JTL: ${JTL}`)
}

main().catch((e) => { console.error('压测失败:', e); process.exit(1) })
