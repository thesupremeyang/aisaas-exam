export interface AssessmentReportContent {
  dimensionScores: Record<string, number>
  totalScore?: number
  confidence: string
  conclusion: string
  evidence: Array<{ dimension: string; content: string }>
  risks: string[]
  suggestions: string[]
}

const emptyReport = (): AssessmentReportContent => ({
  dimensionScores: {},
  totalScore: undefined,
  confidence: '',
  conclusion: '',
  evidence: [],
  risks: [],
  suggestions: [],
})

export function decodeAssessmentReport(value?: string | null): AssessmentReportContent {
  if (!value) return emptyReport()
  try {
    const parsed = JSON.parse(value) as Partial<AssessmentReportContent>
    const rawEvidence: unknown = parsed.evidence
    const rawRisks: unknown = parsed.risks
    const rawSuggestions: unknown = parsed.suggestions
    return {
      dimensionScores:
        parsed.dimensionScores && typeof parsed.dimensionScores === 'object'
          ? parsed.dimensionScores
          : {},
      totalScore: typeof parsed.totalScore === 'number' ? parsed.totalScore : undefined,
      confidence: typeof parsed.confidence === 'string' ? parsed.confidence : '',
      conclusion: typeof parsed.conclusion === 'string' ? parsed.conclusion : '',
      evidence: Array.isArray(rawEvidence)
        ? rawEvidence
            .filter((item): item is { dimension?: unknown; content?: unknown } => !!item && typeof item === 'object')
            .map((item) => ({ dimension: typeof item.dimension === 'string' ? item.dimension : '综合材料', content: typeof item.content === 'string' ? item.content : '' }))
            .filter((item) => !!item.content)
        : typeof rawEvidence === 'string' ? [{ dimension: '综合材料', content: rawEvidence }] : [],
      risks: Array.isArray(rawRisks) ? rawRisks.filter((item): item is string => typeof item === 'string') : [],
      suggestions: Array.isArray(rawSuggestions) ? rawSuggestions.filter((item): item is string => typeof item === 'string') : [],
    }
  } catch {
    return emptyReport()
  }
}
