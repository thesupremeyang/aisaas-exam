#!/usr/bin/env node
/**
 * 实操考试系统压测造数脚本
 *
 * 对齐理论考试系统 LOADTEST-METHOD.md §3.3 的四要素：
 *   题目 / 场次（每档一场新的，绝不复用）/ 名单 / exam.json
 *
 * 与理论系统的差异（本系统特有）：
 *   - 交卷要求 submission.agentId 非空且属于考生自己的租户，
 *     所以造数阶段必须给每个考生租户复制一个智能体（cloneAgentSql）。
 *   - 建场次用 startAt + durationMinutes，不是起止时间对。
 *
 * 用法：
 *   node setup-exam.mjs --users=500
 *   NO_OPEN=1 node setup-exam.mjs --users=500   # 建完保持草稿，压测时再开考
 */
import { execFileSync } from 'node:child_process'
import { writeFileSync, mkdirSync } from 'node:fs'
import { createHash } from 'node:crypto'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = dirname(fileURLToPath(import.meta.url))
const OUT = join(HERE, 'artifacts')
mkdirSync(OUT, { recursive: true })

const args = Object.fromEntries(
  process.argv.slice(2).map((a) => {
    const [k, v = 'true'] = a.replace(/^--/, '').split('=')
    return [k, v]
  })
)

const BASE = (process.env.EXAM_BASE || 'http://127.0.0.1:3160/api').replace(/\/$/, '')
const USERS = Number(args.users || process.env.USERS || 200)
const NO_OPEN = process.env.NO_OPEN === '1'
const TAG = args.tag || new Date().toISOString().slice(11, 19).replace(/:/g, '')
const PREFIX = `lt${TAG}` // 每档一批独立账号，避免复用已交卷考生
const PASSWORD = createHash('md5').update('Test@1234').digest('hex')

// MySQL 访问（只为给考生租户复制智能体）
const MYSQL = {
  host: process.env.DB_HOST || '127.0.0.1',
  port: process.env.DB_PORT || '3306',
  db: process.env.DB_NAME || 'apboa_exam',
  user: process.env.DB_USER || 'root',
  pass: process.env.DB_PASS || '123456',
}

function sql(query) {
  return execFileSync('mysql', [
    `-u${MYSQL.user}`, `-p${MYSQL.pass}`, `-h${MYSQL.host}`, `-P${MYSQL.port}`,
    MYSQL.db, '-N', '-B', '-e', query,
  ], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 })
}

async function api(method, path, { token, body } = {}) {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const json = await res.json().catch(() => ({}))
  return { status: res.status, json }
}

const need = (cond, msg) => {
  if (!cond) throw new Error(msg)
}

const fmt = (d) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ` +
  `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`

async function main() {
  // 1. 管理员登录
  const login = await api('POST', '/auth/login', {
    body: { username: 'admin', password: '277fc0217db5d364b3b886a9672ea9d3' },
  })
  const adminToken = login.json?.data?.accessToken
  need(adminToken, `管理员登录失败: ${JSON.stringify(login.json).slice(0, 200)}`)
  console.log('[1/6] 管理员登录 OK')

  // 2. 建场次（每档一场新的）
  const now = new Date()
  const startAt = new Date(now.getTime() + 2 * 60 * 1000)
  const camp = await api('POST', '/exam/admin/campaigns', {
    token: adminToken,
    body: {
      name: `压测-${PREFIX}`,
      startAt: fmt(startAt),
      durationMinutes: 240,
      enabled: true,
    },
  })
  const campaignId = camp.json?.data?.id
  need(campaignId, `建场次失败: ${JSON.stringify(camp.json).slice(0, 200)}`)
  console.log(`[2/6] 场次创建 OK  campaignId=${campaignId}`)

  // 3. 建题目
  const q = await api('POST', `/exam/admin/campaigns/${campaignId}/questions`, {
    token: adminToken,
    body: {
      title: '压测题：搭建一个门店天气查询智能体',
      score: 100,
      suggestedMinutes: 30,
      taskScenario: '连锁门店需要每日天气提醒',
      taskRequirement: '配置一个可查询天气并返回结构化结果的智能体',
      submissionDeliverable: '智能体配置与说明',
      sortOrder: 1,
    },
  })
  const questionId = q.json?.data?.id
  need(questionId, `建题目失败: ${JSON.stringify(q.json).slice(0, 200)}`)
  console.log(`[3/6] 题目创建 OK  questionId=${questionId}`)

  // 4. 批量建考生（限流并发，避免把接口打歪）
  const CONC = 20
  const enrollments = new Map() // username -> enrollmentId
  let done = 0
  for (let i = 0; i < USERS; i += CONC) {
    const batch = []
    for (let j = i; j < Math.min(i + CONC, USERS); j++) {
      const username = `${PREFIX}u${String(j).padStart(4, '0')}`
      batch.push(
        api('POST', `/exam/admin/campaigns/${campaignId}/candidates`, {
          token: adminToken,
          body: {
            nickname: username.slice(-10),
            username,
            email: `${username}@exam.local`,
            password: PASSWORD,
            organization: '压测班',
          },
        }).then((r) => {
          const id = r.json?.data
          if (id) enrollments.set(username, String(id))
          return r
        })
      )
    }
    await Promise.all(batch)
    done = Math.min(i + CONC, USERS)
    if (done % 100 === 0 || done === USERS) console.log(`[4/6] 建考生 ${done}/${USERS}`)
  }
  need(enrollments.size === USERS, `考生只建成 ${enrollments.size}/${USERS}`)

  // 5. 给每个考生租户复制一个智能体（本系统交卷的硬性前提）
  const usernames = [...enrollments.keys()]
  const inList = usernames.map((u) => `'${u}'`).join(',')
  const tenantRows = sql(
    `SELECT a.username, at.tenant_id FROM account a
     JOIN account_tenant at ON at.account_id = a.id
     WHERE a.username IN (${inList})`
  ).trim().split('\n').filter(Boolean)
  const tenantOf = new Map(tenantRows.map((r) => r.split('\t')))
  need(tenantOf.size >= USERS, `只取到 ${tenantOf.size}/${USERS} 个租户`)

  // 显式指定新 id，做到「租户 ↔ 智能体」一一对应。
  // 不能靠 agent_code 后缀回查：REPLACE 生成后缀不可靠，且 LIKE 里的 _ 是通配符，
  // 会让多个考生映射到同一个（别人的）智能体，交卷时全部报「不属于当前账号」。
  const tenants = [...new Set(tenantOf.values())]
  const srcAgent = sql(`SELECT id FROM agent_definition WHERE tenant_id = 1 LIMIT 1`).trim()
  need(srcAgent, '找不到模板智能体（tenant_id=1）')

  const cols = sql(`
    SELECT GROUP_CONCAT(COLUMN_NAME) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA='${MYSQL.db}' AND TABLE_NAME='agent_definition'
      AND COLUMN_NAME NOT IN ('id','tenant_id')
  `).trim()
  need(cols, '读不到 agent_definition 列清单')

  const genId = () =>
    `${Date.now()}${String(Math.floor(Math.random() * 1e6)).padStart(6, '0')}`

  const agentOf = new Map()
  const BATCH = 50
  for (let i = 0; i < tenants.length; i += BATCH) {
    const chunk = tenants.slice(i, i + BATCH)
    const parts = chunk.map((t) => {
      const id = genId()
      agentOf.set(t, id)
      // agent_code 加 id 后缀，避免与模板及彼此冲突
      const vals = cols.replace('agent_code', `CONCAT(agent_code,'_${id}')`)
      return `SELECT ${id}, ${t}, ${vals} FROM agent_definition WHERE id = ${srcAgent}`
    }).join(' UNION ALL ')
    sql(`INSERT INTO agent_definition (id, tenant_id, ${cols}) ${parts}`)
  }
  console.log(`[5/6] 智能体复制 OK  租户数=${tenants.length}`)

  // 6. 写 exam.json + candidates.csv
  const candidates = []
  for (const [username, enrollmentId] of enrollments) {
    const tenantId = tenantOf.get(username)
    const agentId = agentOf.get(tenantId)
    if (!agentId) throw new Error(`租户 ${tenantId} 没有分到智能体`)
    candidates.push({ username, password: PASSWORD, enrollmentId, tenantId, agentId })
  }

  const exam = {
    base: BASE,
    campaignId: String(campaignId),
    questionId: String(questionId),
    prefix: PREFIX,
    users: USERS,
    createdAt: new Date().toISOString(),
    opened: !NO_OPEN,
  }
  writeFileSync(join(OUT, 'exam.json'), JSON.stringify(exam, null, 2))
  writeFileSync(join(OUT, 'candidates.json'), JSON.stringify(candidates))

  if (!NO_OPEN) {
    await api('POST', `/exam/admin/campaigns/${campaignId}/start-now`, { token: adminToken })
    exam.opened = true
    writeFileSync(join(OUT, 'exam.json'), JSON.stringify(exam, null, 2))
    console.log('[6/6] 已开考 (start-now)')
  } else {
    console.log('[6/6] NO_OPEN=1，保持草稿，压测时由 0 号线程开考')
  }

  console.log(`\n造数完成：${USERS} 名考生`)
  console.log(`  campaignId=${campaignId}  questionId=${questionId}`)
  console.log(`  产物：${join(OUT, 'exam.json')}`)
}

main().catch((e) => {
  console.error('造数失败:', e.message)
  process.exit(1)
})
