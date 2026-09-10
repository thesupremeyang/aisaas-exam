import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import ts from 'typescript'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const sourcePath = path.join(__dirname, 'creatorStepGuard.ts')
const source = fs.readFileSync(sourcePath, 'utf8')
const chatSource = fs.readFileSync(path.join(__dirname, 'index.vue'), 'utf8')
const compiled = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.ESNext,
    target: ts.ScriptTarget.ES2022,
  },
}).outputText

const dataUrl = `data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`
const {
  isCreatorAwaitingConfirmation,
  findCreatorConfirmationIndex,
  isCreatorBuildTimedOut,
  shouldPollCreatorRunStatus,
  extractCreatorResultRows,
  mergeCreatorResultRows,
  extractCreatedAgentFromText,
  extractCreatedAgentFromMessages,
} = await import(dataUrl)

assert.equal(
  isCreatorAwaitingConfirmation(
    '你好，我是小图。我会先帮你把需求梳理清楚，确认一次配置后就自动完成创建。'
  ),
  false,
  'greeting/introduction copy should not move the creator flow to confirmation',
)

assert.equal(
  isCreatorAwaitingConfirmation(
    '我理解的配置是：名称“销售跟进助手”；服务对象是一线销售；核心目标是整理客户聊天记录；核心能力：需求摘要、跟进建议、联系提醒。确认按这个方案创建吗？'
  ),
  true,
  'configuration summary with an explicit creation question should ask for confirmation',
)

assert.equal(
  findCreatorConfirmationIndex([
    { role: 'user', content: '你好' },
    { role: 'assistant', content: '我还需要了解你的目标用户。' },
  ]),
  -1,
  'casual user messages should not count as confirmation',
)

assert.equal(
  findCreatorConfirmationIndex([
    { role: 'user', content: '确认通过，按这个方案创建。' },
  ]),
  0,
  'explicit user confirmation should lock the build step',
)

assert.equal(
  isCreatorBuildTimedOut(1_000, 181_000, 180_000),
  true,
  'creator build should time out once it exceeds the configured limit',
)

assert.equal(
  isCreatorBuildTimedOut(1_000, 120_000, 180_000),
  false,
  'creator build should keep running before the configured limit',
)

assert.equal(
  shouldPollCreatorRunStatus('101', '101', true, 3),
  true,
  'active creator build should poll the current session so a stale SSE connection cannot leave progress at 92%',
)

assert.equal(
  shouldPollCreatorRunStatus('101', '101', false, 3),
  false,
  'normal current session should still rely on the live SSE connection',
)

assert.equal(
  shouldPollCreatorRunStatus('102', '101', false, 1),
  true,
  'background sessions should still be polled',
)

const timeoutHandlerMatch = chatSource.match(/async function handleCreatorBuildTimeout\(\) \{[\s\S]*?\n\}/)
assert.ok(timeoutHandlerMatch, 'creator timeout handler should exist')
const timeoutHandler = timeoutHandlerMatch[0]
assert.ok(
  timeoutHandler.indexOf('onCreatorTimeoutMessage') >= 0
    && timeoutHandler.indexOf('onCreatorTimeoutMessage') < timeoutHandler.indexOf('abortRun'),
  'creator timeout should release the UI and show the timeout message before waiting for abortRun',
)

const tableResult = `
创建完成，智能体已经启用。

| 项目 | 内容 |
| --- | --- |
| 智能体名称 | 销售跟进助手 |
| 智能体 ID | 12345 |
| 使用入口 | 我的智能体 |
`

assert.deepEqual(
  extractCreatedAgentFromText(tableResult),
  {
    id: '12345',
    name: '销售跟进助手',
  },
  'creator result parser should read the created agent id and name from markdown tables',
)

assert.deepEqual(
  extractCreatedAgentFromMessages([
    { role: 'assistant', content: '创建成功：智能体名称：旧智能体\n智能体 ID：111' },
    { role: 'assistant', content: tableResult },
  ]),
  {
    id: '12345',
    name: '销售跟进助手',
  },
  'creator result parser should use the latest successful result in the session',
)

const richResult = `
创建成功，智能体已完成部署与启用。

- 智能体名称：文章摘要助手
- 智能体编号：2099887766554433221
- 模型名称：Qwen Plus
- 分类：文档写作
- 当前算力：文本推理
- 使用技能：网页内容读取、长文摘要
- 使用入口：前往我的智能体即可查看和使用
`

assert.deepEqual(
  extractCreatedAgentFromText(richResult),
  {
    id: '2099887766554433221',
    name: '文章摘要助手',
  },
  'creator result parser should read created agent id from common id aliases',
)

assert.deepEqual(
  extractCreatorResultRows(richResult),
  [
    { label: '智能体名称', value: '文章摘要助手' },
    { label: '智能体 ID', value: '2099887766554433221' },
    { label: '模型名称', value: 'Qwen Plus' },
    { label: '分类', value: '文档写作' },
    { label: '当前算力', value: '文本推理' },
    { label: '使用技能', value: '网页内容读取、长文摘要' },
    { label: '使用入口', value: '前往我的智能体即可查看和使用' },
  ],
  'creator result rows should keep model, category, compute, and skill details for the success card',
)

assert.deepEqual(
  mergeCreatorResultRows(
    [
      { label: '智能体名称', value: '文章摘要助手' },
      { label: '使用入口', value: '前往我的智能体即可查看和使用' },
    ],
    [
      { label: '智能体 ID', value: '2099887766554433221' },
      { label: '模型名称', value: 'Qwen Plus' },
      { label: '分类', value: '文档写作' },
      { label: '当前算力', value: '文本推理' },
      { label: '使用技能', value: '网页内容读取、长文摘要' },
    ],
  ),
  [
    { label: '智能体名称', value: '文章摘要助手' },
    { label: '智能体 ID', value: '2099887766554433221' },
    { label: '模型名称', value: 'Qwen Plus' },
    { label: '分类', value: '文档写作' },
    { label: '当前算力', value: '文本推理' },
    { label: '使用技能', value: '网页内容读取、长文摘要' },
    { label: '使用入口', value: '前往我的智能体即可查看和使用' },
  ],
  'creator result rows should be enriched without losing existing parsed rows',
)

assert.ok(
  chatSource.includes('立即跟新建立的智能体对话'),
  'creator success card should expose the requested direct chat button copy',
)
