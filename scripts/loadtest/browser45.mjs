#!/usr/bin/env node
/**
 * 浏览器级并发彩排（L4）
 *
 * 每个"考生"是一个独立的 Chrome 上下文：打开考试页 → 登录 → 进考场 → 打开作品编辑器
 * → 填身份核验 → 下一步 → 保存草稿。全程记录各阶段耗时、JS 报错、失败请求、WS 连接结果。
 *
 * 用法：
 *   node run45.mjs --n=45 --wave=45 --base=http://117.72.185.237:18080/web/
 *   node run45.mjs --n=3  --wave=3            # 先小规模验证
 */

import { chromium } from 'playwright-core'
import { writeFileSync } from 'node:fs'

const args = Object.fromEntries(
  process.argv.slice(2).map((a) => {
    const [k, v = 'true'] = a.replace(/^--/, '').split('=')
    return [k, v]
  })
)

const BASE = args.base || 'http://117.72.185.237:18080/web/'
const EXEC = args.chrome || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
const N = Number(args.n || 45)
const WAVE = Number(args.wave || N)
const OUT = args.out || `/tmp/bt/result-${N}.json`
const PWD = 'Drill@2026'

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

function pct(arr, p) {
  if (!arr.length) return -1
  const s = [...arr].sort((a, b) => a - b)
  return s[Math.min(s.length - 1, Math.max(0, Math.ceil((p / 100) * s.length) - 1))]
}

const browser = await chromium.launch({
  executablePath: EXEC,
  headless: true,
  args: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-gpu']
})

const rows = []

async function one(i) {
  const username = `lt45_${String(i).padStart(2, '0')}`
  const row = {
    idx: i, user: username, ok: false, stage: 'start',
    bootMs: -1, loginMs: -1, portalMs: -1, editorMs: -1, step2Ms: -1, saveMs: -1,
    jsErrors: [], httpErrors: [], ws: 0, err: ''
  }
  const ctx = await browser.newContext({ viewport: { width: 1366, height: 820 } })
  const page = await ctx.newPage()
  page.on('console', (m) => { if (m.type() === 'error') row.jsErrors.push(m.text().slice(0, 120)) })
  page.on('pageerror', (e) => row.jsErrors.push('PAGEERROR ' + String(e).slice(0, 120)))
  page.on('websocket', () => { row.ws++ })
  page.on('requestfailed', (r) => row.httpErrors.push(`${r.failure()?.errorText} ${r.url().slice(-60)}`))
  page.on('response', (r) => {
    if (r.status() >= 400) row.httpErrors.push(`HTTP${r.status()} ${r.url().slice(-60)}`)
  })


  try {
    // 1) 登录
    // 首屏加载（含 1.6MB 主包）单独计时：走公网时这一段主要受带宽限制
    const tBoot = Date.now()
    await page.goto(BASE, { waitUntil: 'commit', timeout: 90000 })
    await page.waitForSelector('input[placeholder="请输入考号"]', { timeout: 90000 })
    row.bootMs = Date.now() - tBoot

    // 登录动作（点击 → 进入考场），这才是后端与前端交互的真实耗时
    const t0 = Date.now()
    await page.fill('input[placeholder="请输入考号"]', username)
    await page.fill('input[placeholder="请输入密码"]', PWD)
    await page.click('button:has-text("登")')
    await page.waitForURL(/exam\/portal/, { timeout: 60000 })
    row.loginMs = Date.now() - t0
    row.stage = 'login'

    // 2) 门户
    const t1 = Date.now()
    await page.waitForSelector('text=考试试题', { timeout: 30000 })
    row.portalMs = Date.now() - t1
    row.stage = 'portal'

    // 3) 打开作品编辑器（新标签页）
    const t2 = Date.now()
    const card = page.locator('[class*="card"]').filter({ hasText: 'Prompt使用相关' }).first()
    const hasDraft = await card.locator('button:has-text("继续编辑")').count()
    let popupPromise = ctx.waitForEvent('page', { timeout: 30000 })
    if (hasDraft > 0) {
      await card.locator('button:has-text("继续编辑")').first().click()
    } else {
      await card.locator('button:has-text("提交作品")').first().click()
      await page.waitForTimeout(1200)
      popupPromise = ctx.waitForEvent('page', { timeout: 30000 })
      await card.locator('button:has-text("继续编辑")').first().click()
    }
    const popup = await popupPromise
    await popup.waitForLoadState('domcontentloaded').catch(() => {})
    await popup.waitForSelector('text=身份核验', { timeout: 30000 })
    row.editorMs = Date.now() - t2
    row.stage = 'editor'

    // 4) 保存草稿（写路径；草稿态表单可能只读，失败不影响测量）
    const t4 = Date.now()
    try {
      await popup.click('button:has-text("保存草稿")', { timeout: 5000 })
      await popup.waitForTimeout(700)
    } catch { /* 按钮不可用时跳过 */ }
    row.saveMs = Date.now() - t4
    row.stage = 'saved'
    row.ok = true
  } catch (e) {
    row.err = String(e.message || e).slice(0, 160)
  } finally {
    await ctx.close().catch(() => {})
    rows.push(row)
    const flag = row.ok ? 'OK ' : 'ERR'
    console.log(
      `${flag} #${String(i).padStart(2, '0')} ${username} stage=${row.stage} ` +
      `boot=${row.bootMs} login=${row.loginMs} portal=${row.portalMs} editor=${row.editorMs} step2=${row.step2Ms} save=${row.saveMs} ` +
      `js=${row.jsErrors.length} http=${row.httpErrors.length}${row.err ? ' :: ' + row.err : ''}`
    )
  }
}

const t0 = Date.now()
for (let start = 1; start <= N; start += WAVE) {
  const batch = []
  for (let i = start; i < Math.min(start + WAVE, N + 1); i++) {
    batch.push(one(i))
    // 错峰 300ms：模拟 45 个人陆续进场，避免把公网带宽当成服务器瓶颈
    await sleep(300)
  }
  console.log(`--- wave ${start}..${Math.min(start + WAVE - 1, N)} ---`)
  await Promise.all(batch)
}
const wall = Date.now() - t0

const ok = rows.filter((r) => r.ok)
const summary = {
  base: BASE, n: N, wave: WAVE, wallMs: wall,
  success: ok.length, failure: rows.length - ok.length,
  successRate: +(ok.length / rows.length).toFixed(4),
  phases: {
    login: { p50: pct(rows.map((r) => r.loginMs).filter((v) => v > 0), 50), p95: pct(rows.map((r) => r.loginMs).filter((v) => v > 0), 95) },
    portal: { p50: pct(rows.map((r) => r.portalMs).filter((v) => v > 0), 50), p95: pct(rows.map((r) => r.portalMs).filter((v) => v > 0), 95) },
    editor: { p50: pct(rows.map((r) => r.editorMs).filter((v) => v > 0), 50), p95: pct(rows.map((r) => r.editorMs).filter((v) => v > 0), 95) },
    step2: { p50: pct(rows.map((r) => r.step2Ms).filter((v) => v > 0), 50), p95: pct(rows.map((r) => r.step2Ms).filter((v) => v > 0), 95) },
    save: { p50: pct(rows.map((r) => r.saveMs).filter((v) => v > 0), 50), p95: pct(rows.map((r) => r.saveMs).filter((v) => v > 0), 95) }
  },
  jsErrorRows: rows.filter((r) => r.jsErrors.length).length,
  httpErrorRows: rows.filter((r) => r.httpErrors.length).length,
  wsConnections: rows.reduce((s, r) => s + r.ws, 0),
  topJsErrors: [...new Set(rows.flatMap((r) => r.jsErrors))].slice(0, 5),
  topHttpErrors: [...new Set(rows.flatMap((r) => r.httpErrors))].slice(0, 5),
  failures: rows.filter((r) => !r.ok).map((r) => ({ user: r.user, stage: r.stage, err: r.err })).slice(0, 10)
}
console.log('\n===== SUMMARY =====')
console.log(JSON.stringify(summary, null, 2))
writeFileSync(OUT, JSON.stringify({ summary, rows }, null, 2))
console.log('-> ' + OUT)

await browser.close()
process.exit(summary.successRate >= 0.99 ? 0 : 1)
