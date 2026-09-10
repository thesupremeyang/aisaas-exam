export type CreatorStepMessage = {
  role?: string
  content?: string | null
}

export type CreatedAgentResult = {
  id: string
  name?: string
}

export type CreatorResultRow = {
  label: string
  value: string
}

const SUMMARY_FIELD_PATTERNS = [
  /(?:智能体)?名称/,
  /服务对象|目标用户|使用对象/,
  /核心目标|主要目标|用途|用于/,
  /核心能力|核心功能|能力清单/,
  /典型输入|输入/,
  /输出|结果/,
  /运行方式|使用方式|触发方式/,
]

function normalizeText(content: string | null | undefined): string {
  return (content || '').replace(/\s+/g, ' ').trim()
}

function cleanCreatorValue(value: string | null | undefined): string {
  return (value || '')
    .replace(/[*`"“”'「」]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
}

function normalizeCreatorResultLabel(label: string): string | null {
  const text = cleanCreatorValue(label)
    .replace(/^[-•\d.、\s]+/, '')
    .replace(/\s+/g, '')
  if (!text || /^(-+|项目|字段|内容)$/.test(text)) return null
  if (/^(智能体)?(ID|编号|标识)$/i.test(text) || /^Agent(ID|Id)$/i.test(text)) return '智能体 ID'
  if (/^(智能体)?名称$/.test(text)) return '智能体名称'
  if (/^(模型|模型名称|模型配置|调用模型)$/.test(text)) return '模型名称'
  if (/^(分类|标签|所属分类|应用分类)$/.test(text)) return '分类'
  if (/^(当前算力|调用算力|算力|模型类型|能力类型)$/.test(text)) return '当前算力'
  if (/^(使用技能|技能|技能包|调用技能|已启用技能)$/.test(text)) return '使用技能'
  if (/^(核心能力|已完成的核心能力|主要能力)$/.test(text)) return '核心能力'
  if (/^(使用入口|入口|访问入口|可访问入口)$/.test(text)) return '使用入口'
  return cleanCreatorValue(label)
}

function addCreatorResultRow(
  rows: CreatorResultRow[],
  label: string | null | undefined,
  value: string | null | undefined,
) {
  const normalizedLabel = label ? normalizeCreatorResultLabel(label) : null
  const normalizedValue = cleanCreatorValue(value)
  if (!normalizedLabel || !normalizedValue || /^-+$/.test(normalizedValue)) return
  const existingIndex = rows.findIndex(row => row.label === normalizedLabel)
  if (existingIndex >= 0) {
    rows[existingIndex] = { label: normalizedLabel, value: normalizedValue }
  } else {
    rows.push({ label: normalizedLabel, value: normalizedValue })
  }
}

function readCreatorRowValue(rows: CreatorResultRow[], label: string | undefined): string {
  if (!label) return ''
  return rows.find(row => row.label === label)?.value || ''
}

const CREATOR_RESULT_FIELD_PATTERNS: Array<{ label: string; pattern: RegExp }> = [
  { label: '智能体名称', pattern: /(?:智能体名称|名称)\s*[：:|]\s*([^\n|]+)/i },
  { label: '智能体 ID', pattern: /(?:智能体\s*(?:ID|编号|标识)|(?:^|\n)\s*ID)\s*[：:|]\s*([0-9A-Za-z_-]+)/i },
  { label: '模型名称', pattern: /(?:模型名称|模型配置|调用模型|模型)\s*[：:|]\s*([^\n|]+)/i },
  { label: '分类', pattern: /(?:分类|标签|所属分类|应用分类)\s*[：:|]\s*([^\n|]+)/i },
  { label: '当前算力', pattern: /(?:当前算力|调用算力|算力|模型类型|能力类型)\s*[：:|]\s*([^\n|]+)/i },
  { label: '使用技能', pattern: /(?:使用技能|调用技能|已启用技能|技能包|技能)\s*[：:|]\s*([^\n|]+)/i },
  { label: '核心能力', pattern: /(?:核心能力|已完成的核心能力|主要能力)\s*[：:|]\s*([^\n|]+)/i },
  { label: '使用入口', pattern: /(?:使用入口|访问入口|可访问入口|入口)\s*[：:|]\s*([^\n]+)/i },
]

const CREATOR_RESULT_LABEL_ORDER = [
  '智能体名称',
  '智能体 ID',
  '模型名称',
  '分类',
  '当前算力',
  '使用技能',
  '核心能力',
  '使用入口',
]

function orderCreatorResultRows(rows: CreatorResultRow[]): CreatorResultRow[] {
  return [...rows].sort((a, b) => {
    const ai = CREATOR_RESULT_LABEL_ORDER.indexOf(a.label)
    const bi = CREATOR_RESULT_LABEL_ORDER.indexOf(b.label)
    if (ai < 0 && bi < 0) return 0
    if (ai < 0) return 1
    if (bi < 0) return -1
    return ai - bi
  })
}

export function extractCreatorResultRows(content: string | null | undefined): CreatorResultRow[] {
  const text = content || ''
  if (!text) return []

  const rows: CreatorResultRow[] = []
  const tableRowPattern = /\|\s*([^|\n]+?)\s*\|\s*([^|\n]+?)\s*\|/g
  let tableMatch: RegExpExecArray | null
  while ((tableMatch = tableRowPattern.exec(text)) !== null) {
    addCreatorResultRow(rows, tableMatch[1], tableMatch[2])
  }

  for (const { label, pattern } of CREATOR_RESULT_FIELD_PATTERNS) {
    addCreatorResultRow(rows, label, text.match(pattern)?.[1])
  }

  if (!readCreatorRowValue(rows, '使用入口') && /我的智能体/.test(text)) {
    addCreatorResultRow(rows, '使用入口', '我的智能体')
  }

  return orderCreatorResultRows(rows)
}

export function mergeCreatorResultRows(
  baseRows: CreatorResultRow[],
  extraRows: CreatorResultRow[],
): CreatorResultRow[] {
  const rows = [...baseRows]
  extraRows.forEach(row => addCreatorResultRow(rows, row.label, row.value))
  return orderCreatorResultRows(rows)
}

export function isCreatorUserConfirmation(content: string | null | undefined): boolean {
  return /^(确认|确认通过|开始创建|按这个方案)(?:[\s，,。！!、]|$)/.test(normalizeText(content))
}

export function findCreatorConfirmationIndex(messages: CreatorStepMessage[]): number {
  for (let index = messages.length - 1; index >= 0; index--) {
    const item = messages[index]
    if (item?.role === 'user' && isCreatorUserConfirmation(item.content)) {
      return index
    }
  }
  return -1
}

export function isCreatorAwaitingConfirmation(content: string | null | undefined): boolean {
  const text = normalizeText(content)
  if (!text || !/[?？]/.test(text)) return false

  const asksToCreate = /(?:确认|是否|请确认).{0,30}(?:方案|配置|摘要|需求).{0,40}(?:创建|生成|部署)/.test(text)
    || /(?:按|以|基于|根据).{0,20}(?:这个|以上|该)?.{0,20}(?:方案|配置|摘要|需求).{0,40}(?:创建|生成|部署).{0,20}(?:吗|么|[?？])/.test(text)
  if (!asksToCreate) return false

  const hasSummaryIntro = /(?:我理解|配置摘要|需求摘要|方案摘要|配置是|方案是|总结如下|如下)/.test(text)
  const summaryFieldCount = SUMMARY_FIELD_PATTERNS
    .filter(pattern => pattern.test(text))
    .length

  return (hasSummaryIntro && summaryFieldCount >= 2) || summaryFieldCount >= 3
}

export function isCreatorBuildTimedOut(
  startedAt: number | null | undefined,
  now: number,
  timeoutMs: number,
): boolean {
  return typeof startedAt === 'number'
    && startedAt > 0
    && now - startedAt >= timeoutMs
}

export function shouldPollCreatorRunStatus(
  sessionId: string,
  currentSessionId: string | null | undefined,
  isCurrentCreatorBuildRunning: boolean,
  creatorStep: number,
): boolean {
  if (sessionId !== currentSessionId) return true
  return isCurrentCreatorBuildRunning && creatorStep === 3
}

export function extractCreatedAgentFromText(content: string | null | undefined): CreatedAgentResult | null {
  const text = content || ''
  if (!/创建完成|创建成功|成功创建|智能体\s*ID/i.test(text)) return null

  const rows = extractCreatorResultRows(text)
  let id = readCreatorRowValue(rows, '智能体 ID')
  let name = readCreatorRowValue(rows, '智能体名称')

  if (!id) {
    id = cleanCreatorValue(text.match(/(?:智能体\s*(?:ID|编号|标识)|(?:^|\n)\s*ID)\s*[：:|]\s*([0-9A-Za-z_-]+)/i)?.[1])
  }
  if (!name) {
    name = cleanCreatorValue(text.match(/(?:智能体名称|名称)\s*[：:|]\s*([^\n|]+)/i)?.[1])
  }

  id = id.match(/\d+/)?.[0] || id
  return id ? { id, ...(name ? { name } : {}) } : null
}

export function extractCreatedAgentFromMessages(messages: CreatorStepMessage[]): CreatedAgentResult | null {
  for (let index = messages.length - 1; index >= 0; index--) {
    const item = messages[index]
    if (item?.role !== 'assistant') continue
    const result = extractCreatedAgentFromText(item.content)
    if (result) return result
  }
  return null
}
