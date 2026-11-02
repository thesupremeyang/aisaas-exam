#!/usr/bin/env node
/**
 * 清理压测造数产生的数据。
 *
 * 为什么必须有这一步：
 *   造数会持续往 account / tenant / agent_definition / exam_* 里灌数据。
 *   不清理的话，每一档压测面对的库都比上一档大，
 *   延迟增长里就混进了「数据量增长」而不是纯粹的「并发增长」——
 *   本轮第一版 A/B 就是这么被污染的：默认配置回归跑出来比调大连接池还慢，
 *   看起来像「调大连接池有害」，实际是库被前几轮造数撑大了。
 *   所以：每档压测前先清理，保证各档面对的库大小一致。
 *
 * 用法：
 *   node cleanup.mjs          # 只报告将删除的数量（dry-run）
 *   node cleanup.mjs --force  # 真正执行删除
 */
import { execFileSync } from 'node:child_process'

const FORCE = process.argv.includes('--force')
const MYSQL = {
  host: process.env.DB_HOST || '127.0.0.1',
  port: process.env.DB_PORT || '3306',
  db: process.env.DB_NAME || 'apboa_exam',
  user: process.env.DB_USER || 'root',
  pass: process.env.DB_PASS || '123456',
}

function sql(q) {
  return execFileSync('mysql', [
    `-u${MYSQL.user}`, `-p${MYSQL.pass}`, `-h${MYSQL.host}`, `-P${MYSQL.port}`,
    MYSQL.db, '-N', '-B', '-e', q,
  ], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 }).trim()
}

const num = (q) => Number(sql(q) || 0)

const counts = {
  campaign: num(`SELECT COUNT(*) FROM exam_campaign WHERE name LIKE '压测-%'`),
  question: num(`SELECT COUNT(*) FROM exam_question WHERE campaign_id IN (SELECT id FROM exam_campaign WHERE name LIKE '压测-%')`),
  enrollment: num(`SELECT COUNT(*) FROM exam_enrollment WHERE campaign_id IN (SELECT id FROM exam_campaign WHERE name LIKE '压测-%')`),
  submission: num(`SELECT COUNT(*) FROM exam_submission WHERE enrollment_id IN (SELECT id FROM exam_enrollment WHERE campaign_id IN (SELECT id FROM exam_campaign WHERE name LIKE '压测-%'))`),
  account: num(`SELECT COUNT(*) FROM account WHERE email LIKE '%@exam.local'`),
  // 压测考生的租户已被上面清掉，用「租户已不存在」识别复制出来的智能体最稳，
  // 不依赖 agent_code 的具体值（模板 code 是 ...trip-20260830，容易写错）。
  agent: num(`SELECT COUNT(*) FROM agent_definition
              WHERE tenant_id NOT IN (SELECT id FROM tenant)`),
}

console.log(FORCE ? '执行清理：' : '待清理数量（加 --force 才会真删）：')
for (const [k, v] of Object.entries(counts)) console.log(`  ${k.padEnd(12)} ${v}`)

if (!FORCE) process.exit(0)

// 顺序：先子表后主表
sql(`
  DELETE FROM exam_submission WHERE enrollment_id IN
    (SELECT id FROM exam_enrollment WHERE campaign_id IN
      (SELECT id FROM exam_campaign WHERE name LIKE '压测-%'));
`)
sql(`DELETE FROM exam_enrollment WHERE campaign_id IN
      (SELECT id FROM exam_campaign WHERE name LIKE '压测-%')`)
sql(`DELETE FROM exam_question WHERE campaign_id IN
      (SELECT id FROM exam_campaign WHERE name LIKE '压测-%')`)
sql(`DELETE FROM exam_campaign WHERE name LIKE '压测-%'`)

sql(`DELETE FROM account_tenant WHERE account_id IN
      (SELECT id FROM account WHERE email LIKE '%@exam.local')`)
sql(`DELETE FROM account WHERE email LIKE '%@exam.local'`)
sql(`DELETE FROM tenant WHERE id NOT IN (SELECT tenant_id FROM account_tenant WHERE tenant_id IS NOT NULL)`)

sql(`DELETE FROM agent_definition WHERE tenant_id NOT IN (SELECT id FROM tenant)`)

console.log('\n清理完成，当前残留：')
console.log(`  压测场次 ${num(`SELECT COUNT(*) FROM exam_campaign WHERE name LIKE '压测-%'`)}`)
console.log(`  压测账号 ${num(`SELECT COUNT(*) FROM account WHERE email LIKE '%@exam.local'`)}`)
console.log(`  account 总数 ${num('SELECT COUNT(*) FROM account')}`)
console.log(`  agent 总数 ${num('SELECT COUNT(*) FROM agent_definition')}`)
