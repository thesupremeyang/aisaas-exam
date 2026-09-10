export interface ExamRubricItem {
  key: string
  label: string
  maximum: number
  description: string
}

const definitions: Record<string, Omit<ExamRubricItem, 'maximum'>> = {
  SCENE_VALUE: { key: 'SCENE_VALUE', label: '场景价值', description: '真实业务痛点、受益对象与应用场景的匹配程度。' },
  FUNCTION_COMPLETENESS: { key: 'FUNCTION_COMPLETENESS', label: '功能完整度', description: '核心输入、处理与输出链路的完整程度。' },
  INNOVATION: { key: 'INNOVATION', label: '创新性', description: '方案创新和实际应用价值。' },
  TECHNICAL_DESCRIPTION: { key: 'TECHNICAL_DESCRIPTION', label: '技术说明', description: '模型、提示词、Skill、MCP 与知识库说明。' },
  VERIFIABILITY: { key: 'VERIFIABILITY', label: '可验证性', description: '演示、材料和异常场景的可复现程度。' },
}

const defaultDimensions: Record<string, number> = {
  SCENE_VALUE: 20, FUNCTION_COMPLETENESS: 20, INNOVATION: 20, TECHNICAL_DESCRIPTION: 20, VERIFIABILITY: 20,
}
const chineseKeys: Record<string, string> = Object.fromEntries(
  Object.values(definitions).map((item) => [item.label, item.key]),
)
function dimensionItems(dimensions: Record<string, unknown>): ExamRubricItem[] {
  return Object.entries(dimensions).map(([key, maximum]) => ({
    ...(definitions[key] || { key, label: key, description: '以本考试批次评分说明为准。' }),
    maximum: Number(maximum) || 0,
  }))
}

export function examRubricFromConfig(configJson?: string): ExamRubricItem[] {
  try {
    const root = JSON.parse(configJson || '{}')
    const rubric = root.rubric || root['评分规则']
    const dimensions = rubric?.dimensions || rubric?.['评分维度']
    if (dimensions && typeof dimensions === 'object') {
      return dimensionItems(Object.fromEntries(Object.entries(dimensions).map(([key, value]) => [chineseKeys[key] || key, value])))
    }
  } catch {
    // Invalid configuration falls back to the published default rubric.
  }
  return dimensionItems(defaultDimensions)
}

export function currentExamRubric(): ExamRubricItem[] {
  return examRubricFromConfig(sessionStorage.getItem('exam:rubricConfig') || undefined)
}
