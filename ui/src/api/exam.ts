import request from '@/utils/request'
import type { AgentDefinitionVO, ApiResponse, BaseEntity } from '@/types'

export interface ExamSubmission extends BaseEntity {
  enrollmentId?: string
  questionId?: string
  agentId?: string
  agentName?: string
  workType?: string
  formJson?: string
  currentStep?: number
  status?: string
  version?: number
  submittedAt?: string
  examState?: 'WAITING' | 'OPEN' | 'ENDED'
  examStartAt?: string
  examEndAt?: string
  serverNow?: string
}

export interface AssessmentTask extends BaseEntity {
  submissionId: string
  taskType: string
  status: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'
  progress?: number
  attempt?: number
  errorCode?: string
  errorMessage?: string
  startedAt?: string
  finishedAt?: string
}

export interface AssessmentReport extends BaseEntity {
  submissionId: string
  taskId: string
  rubricVersion?: string
  promptVersion?: string
  modelName?: string
  reportJson?: string
  totalScore?: number
  confidence?: string
}

export interface AssessmentReview extends BaseEntity {
  submissionId: string
  reviewerAccountId?: string
  scoresJson?: string
  totalScore?: number
  comment?: string
  checklistJson?: string
  status?: 'DRAFT' | 'PUBLISHED'
}

const candidatePath = (submissionId: string) => `/api/exam/candidate/submissions/${submissionId}`
const reviewerPath = (submissionId: string) =>
  `/api/exam/reviewer/submissions/${submissionId}/review`

export function getCandidateSubmission(submissionId: string) {
  return request.get<ApiResponse<ExamSubmission>>(candidatePath(submissionId))
}

/** 查分模式：获取本人已提交作品关联的智能体配置，只读。 */
export function getCandidateAgentConfiguration(submissionId: string) {
  return request.get<ApiResponse<AgentDefinitionVO>>(`${candidatePath(submissionId)}/agent-config`, {
    timeout: 10000,
  })
}

/** 列出某报名下的全部作品（作品集） */
export function listSubmissions(enrollmentId: string | number) {
  return request.get<ApiResponse<ExamSubmission[]>>(
    `/api/exam/candidate/submissions/enrollments/${enrollmentId}/submissions`,
    { timeout: 10000 },
  )
}

/** 在同一报名下新增一份草稿作品（绑定指定试题） */
export function createSubmission(enrollmentId: string | number, questionId?: string | number) {
  return request.post<ApiResponse<ExamSubmission>>(
    `/api/exam/candidate/submissions/enrollments/${enrollmentId}/submissions`,
    questionId ? { questionId } : {},
    { timeout: 10000 },
  )
}

/** 删除草稿作品（仅限本人、仅草稿） */
export function deleteSubmission(submissionId: string | number) {
  return request.delete<ApiResponse<boolean>>(candidatePath(String(submissionId)))
}

export function saveCandidateDraft(submissionId: string, payload: ExamSubmission) {
  return request.put<ApiResponse<boolean>>(`${candidatePath(submissionId)}/draft`, payload)
}

export function submitCandidateWork(submissionId: string) {
  return request.post<ApiResponse<ExamSubmission>>(`${candidatePath(submissionId)}/submit`)
}

/** 评测失败时，使用当前已提交作品重新排队 AI 智评 */
export function retryCandidateAssessment(submissionId: string) {
  return request.post<ApiResponse<ExamSubmission>>(`${candidatePath(submissionId)}/retry-assessment`)
}

export function getAssessmentTask(submissionId: string) {
  return request.get<ApiResponse<AssessmentTask>>(`${candidatePath(submissionId)}/assessment-task`)
}

export function getAssessmentReport(submissionId: string) {
  return request.get<ApiResponse<AssessmentReport>>(
    `${candidatePath(submissionId)}/assessment-report`,
  )
}

export function getReviewerAssessment(submissionId: string) {
  return request.get<ApiResponse<AssessmentReview>>(reviewerPath(submissionId))
}

export function saveReviewerAssessment(submissionId: string, payload: AssessmentReview) {
  return request.put<ApiResponse<boolean>>(reviewerPath(submissionId), payload)
}

export function publishReviewerAssessment(submissionId: string) {
  return request.post<ApiResponse<AssessmentReview>>(`${reviewerPath(submissionId)}/publish`)
}

// ============ 学员端考试门户 ============

export interface ExamCampaign extends BaseEntity {
  name?: string
  registrationStart?: string
  registrationEnd?: string
  submissionStart?: string
  submissionEnd?: string
  reviewEnd?: string
  status?: string
  rubricVersion?: string
  configJson?: string
  scoreQueryEnabled?: boolean
}

export interface ExamAgentSummaryVO {
  id?: string | number
  name?: string
  description?: string
  avatar?: string
  skills?: string[]
  mcps?: string[]
  knowledgeBases?: string[]
}

export interface ExamSsoTicketIssueVO {
  ticket?: string
  returnUrl?: string
  expiresAt?: string
}

export interface ExamSsoTicketConsumeVO {
  accountId?: string | number
  tenantId?: string | number
  campaignId?: string | number
  returnUrl?: string
}

export interface CandidateProgress {
  enrolled?: boolean
  enrollmentId?: string | number
  submissionId?: string | number
  submissionStatus?: string
  aiPublished?: boolean
  registrationOpen?: boolean
  submissionOpen?: boolean
  scoreQueryEnabled?: boolean
  examState?: 'WAITING' | 'OPEN' | 'ENDED'
  examStartAt?: string
  examEndAt?: string
  serverNow?: string
  maxWorks?: number
}

export interface ExamCandidateModelConfig {
  configId?: string | number
  providerKey?: string
  schemeId?: string
  providerName?: string
  modelId?: string
  baseUrl?: string
  apiKeyConfigured?: boolean
  connectivityStatus?: 'NOT_CHECKED' | 'CHECKING' | 'CONNECTED' | 'FAILED'
  connectivityMessage?: string
  lastConnectivityCheck?: string
}

export interface ExamCandidateModelConfigRequest {
  providerKey: string
  schemeId?: string
  modelId?: string
  apiKey?: string
  baseUrl?: string
}

export interface ExamCandidateStatus {
  enrollmentId?: string | number
  accountId?: string | number
  candidateName?: string
  username?: string
  organization?: string
  loggedIn?: boolean
  entered?: boolean
  enteredAt?: string
  submissionStatus?: string
  examState?: 'WAITING' | 'OPEN' | 'ENDED'
  elapsedSeconds?: number
  remainingSeconds?: number
}

export interface ExamCandidateStatusSnapshot {
  campaignId?: string | number
  campaignName?: string
  examState?: 'WAITING' | 'OPEN' | 'ENDED'
  examStartAt?: string
  examEndAt?: string
  serverNow?: string
  loginStatusAvailable?: boolean
  totalCandidates?: number
  loggedInCount?: number
  enteredCount?: number
  submittedCount?: number
  candidates?: ExamCandidateStatus[]
}

const candidatePortalPath = '/api/exam/candidate'

export interface ExamCandidateScore {
  campaignId?: string | number
  campaignName?: string
  totalScore?: number | null
  theoryScore?: number | null
  practicalScore?: number | null
  totalScoreMax?: number
  theoryScoreMax?: number
  practicalScoreMax?: number
  questions?: ExamCandidateQuestionScore[]
}

export interface ExamCandidateQuestionScore {
  questionNo?: number
  questionId?: string | number
  questionTitle?: string
  questionScore?: number
  submissionId?: string | number
  agentId?: string | number
  agentName?: string
}

export function listActiveCampaigns() {
  return request.get<ApiResponse<ExamCampaign[]>>(`${candidatePortalPath}/campaigns/active`, {
    timeout: 10000,
  })
}

export function getCandidateProgress(campaignId: string | number) {
  return request.get<ApiResponse<CandidateProgress>>(`${candidatePortalPath}/progress`, {
    params: { campaignId },
    timeout: 10000,
  })
}

/** 查分模式：获取当前考生本人的成绩与已提交作品入口。 */
export function getCandidateScore(campaignId: string | number) {
  return request.get<ApiResponse<ExamCandidateScore>>(`${candidatePortalPath}/score`, {
    params: { campaignId },
    timeout: 10000,
  })
}

/** 考生端：获取指定批次的试题列表 */
export function listCandidateQuestions(campaignId: string | number) {
  return request.get<ApiResponse<ExamQuestion[]>>(`${candidatePortalPath}/questions`, {
    params: { campaignId },
    timeout: 10000,
  })
}

export function getExamCandidateModelConfig(campaignId: string | number) {
  return request.get<ApiResponse<ExamCandidateModelConfig | null>>(
    `${candidatePortalPath}/model-config`,
    {
      params: { campaignId },
      timeout: 10000,
    },
  )
}

export function saveExamCandidateModelConfig(
  campaignId: string | number,
  payload: ExamCandidateModelConfigRequest,
) {
  return request.put<ApiResponse<ExamCandidateModelConfig>>(
    `${candidatePortalPath}/model-config`,
    payload,
    {
      params: { campaignId },
      timeout: 20000,
    },
  )
}

export function listExamCandidateAvailableModels(
  campaignId: string | number,
  payload: ExamCandidateModelConfigRequest,
) {
  return request.post<ApiResponse<string[]>>(
    `${candidatePortalPath}/model-config/models`,
    payload,
    {
      params: { campaignId },
      timeout: 20000,
    },
  )
}

export function getExamCandidateStatus(campaignId: string | number) {
  return request.get<ApiResponse<ExamCandidateStatusSnapshot>>(
    `/api/exam/admin/campaigns/${campaignId}/candidates/status`,
    { timeout: 10000 },
  )
}

export interface ExamCampaignScheduleRequest {
  startAt: string
  durationMinutes: number
}

export interface ExamCampaignUpdateRequest extends ExamCampaignScheduleRequest {
  name: string
  enabled?: boolean
}

export function listExamCampaignsForAdmin() {
  return request.get<ApiResponse<ExamCampaign[]>>('/api/exam/admin/campaigns', {
    timeout: 10000,
  })
}

export function updateExamCampaignSchedule(
  campaignId: string | number,
  payload: ExamCampaignScheduleRequest,
) {
  return request.put<ApiResponse<ExamCampaign>>(
    `/api/exam/admin/campaigns/${campaignId}/schedule`,
    payload,
    { timeout: 10000 },
  )
}

export function createExamCampaign(payload: ExamCampaignUpdateRequest) {
  return request.post<ApiResponse<ExamCampaign>>('/api/exam/admin/campaigns', payload, {
    timeout: 10000,
  })
}

export function updateExamCampaign(
  campaignId: string | number,
  payload: ExamCampaignUpdateRequest,
) {
  return request.put<ApiResponse<ExamCampaign>>(
    `/api/exam/admin/campaigns/${campaignId}`,
    payload,
    { timeout: 10000 },
  )
}

export function updateExamCampaignEnabled(campaignId: string | number, enabled: boolean) {
  return request.patch<ApiResponse<ExamCampaign>>(
    `/api/exam/admin/campaigns/${campaignId}/enabled`,
    null,
    { params: { enabled }, timeout: 10000 },
  )
}

export function updateExamCampaignScoreQuery(campaignId: string | number, enabled: boolean) {
  return request.patch<ApiResponse<ExamCampaign>>(
    `/api/exam/admin/campaigns/${campaignId}/score-query`,
    null,
    { params: { enabled }, timeout: 10000 },
  )
}

export function startExamNow(campaignId: string | number) {
  return request.post<ApiResponse<ExamCampaign>>(
    `/api/exam/admin/campaigns/${campaignId}/start-now`,
    null,
    { timeout: 10000 },
  )
}

export function listCandidateAgents(campaignId: string | number) {
  return request.get<ApiResponse<ExamAgentSummaryVO[]>>(`${candidatePortalPath}/agents`, {
    params: { campaignId },
    timeout: 10000,
  })
}

export function issueSsoTicket(campaignId: string | number, returnUrl: string) {
  return request.post<ApiResponse<ExamSsoTicketIssueVO>>(
    '/api/exam/sso/tickets',
    {
      campaignId,
      returnUrl,
    },
    {
      timeout: 10000,
    },
  )
}

export function consumeSsoTicket(ticket: string) {
  return request.post<ApiResponse<ExamSsoTicketConsumeVO>>(
    `/api/exam/sso/tickets/${encodeURIComponent(ticket)}/consume`,
    undefined,
    { timeout: 10000 },
  )
}

// ============ 评审端：学员 AI 自评分数 ============

export interface ExamScoreRow {
  enrollmentId?: string | number
  submissionId?: string | number
  candidateName?: string
  identityNumber?: string
  phone?: string
  loginName?: string
  organization?: string
  tenantName?: string
  workType?: string
  workTypeLabel?: string
  workName?: string
  demoUrl?: string
  submissionStatus?: string
  submittedAt?: string
  questionCount?: number
  submittedQuestionCount?: number
  aiScoredQuestionCount?: number
  reviewedQuestionCount?: number
  demoCount?: number
  aiTotalScore?: number
  totalLimit?: number
  confidence?: string
  modelName?: string
  aiScoredAt?: string
  finalScore?: number
  reviewComment?: string
  reviewed?: boolean
}

export function listReviewerScores(campaignId?: string | number, name?: string) {
  return request.get<ApiResponse<ExamScoreRow[]>>('/api/exam/reviewer/scores', {
    params: { campaignId, name },
    timeout: 10000,
  })
}

export function exportReviewerScores(campaignId: string | number) {
  return request.get(`/api/exam/reviewer/scores/export`, {
    params: { campaignId },
    responseType: 'blob',
    timeout: 60000,
  })
}

export function saveReviewerScore(
  submissionId: string | number,
  finalScore: number,
) {
  return request.put<ApiResponse<boolean>>(`/api/exam/reviewer/scores/${submissionId}`, null, {
    params: { finalScore },
    timeout: 10000,
  })
}

export interface SubmissionFileItem {
  url?: string
  name?: string
  size?: number
}

export interface SubmissionDimEvidence {
  dimension?: string
  content?: string
}

export interface ExamQuestionScore {
  questionNo?: number
  questionId?: string | number
  questionTitle?: string
  questionScenario?: string
  questionScore?: number
  submissionId?: string | number
  agentId?: string | number
  agentName?: string
  workName?: string
  workType?: string
  workTypeLabel?: string
  submissionStatus?: string
  submittedAt?: string
  scene?: string
  functionDescription?: string
  innovationDescription?: string
  promotionDescription?: string
  dataDescription?: string
  runtimeEnvironment?: string
  teamDescription?: string
  technicalDescription?: string
  hasSkill?: boolean
  hasMcp?: boolean
  hasKnowledge?: boolean
  hasDemo?: boolean
  demoUrl?: string
  materialDescription?: string
  screenshotFiles?: SubmissionFileItem[]
  videoFiles?: SubmissionFileItem[]
  packageFiles?: SubmissionFileItem[]
  supplementaryFiles?: SubmissionFileItem[]
  aiScore?: number
  aiRawScore?: number
  rawScoreLimit?: number
  dimensionScores?: Record<string, number>
  confidence?: string
  modelName?: string
  aiScoredAt?: string
  conclusion?: string
  evidence?: SubmissionDimEvidence[]
  risks?: string[]
  suggestions?: string[]
  finalScore?: number
  finalRawScore?: number
  reviewDimensionScores?: Record<string, number>
  reviewComment?: string
  reviewed?: boolean
}

export interface ExamSubmissionDetail {
  enrollmentId?: string | number
  submissionId?: number
  candidateName?: string
  identityNumber?: string
  loginName?: string
  organization?: string
  tenantName?: string
  phone?: string
  email?: string
  workName?: string
  workType?: string
  workTypeLabel?: string
  submissionStatus?: string
  submittedAt?: string
  agentId?: string
  agentName?: string
  scene?: string
  functionDescription?: string
  innovationDescription?: string
  promotionDescription?: string
  dataDescription?: string
  runtimeEnvironment?: string
  teamDescription?: string
  technicalDescription?: string
  hasSkill?: boolean
  hasMcp?: boolean
  hasKnowledge?: boolean
  hasDemo?: boolean
  demoUrl?: string
  materialDescription?: string
  screenshotFiles?: SubmissionFileItem[]
  videoFiles?: SubmissionFileItem[]
  packageFiles?: SubmissionFileItem[]
  supplementaryFiles?: SubmissionFileItem[]
  dimensionScores?: Record<string, number>
  aiTotalScore?: number
  totalLimit?: number
  confidence?: string
  modelName?: string
  aiScoredAt?: string
  conclusion?: string
  evidence?: SubmissionDimEvidence[]
  risks?: string[]
  suggestions?: string[]
  finalScore?: number
  reviewComment?: string
  reviewed?: boolean
  questionCount?: number
  submittedQuestionCount?: number
  aiScoredQuestionCount?: number
  reviewedQuestionCount?: number
  demoCount?: number
  questions?: ExamQuestionScore[]
}

export function getSubmissionDetail(submissionId: string | number) {
  return request.get<ApiResponse<ExamSubmissionDetail>>(
    `/api/exam/reviewer/scores/${submissionId}/detail`,
    {
      timeout: 10000,
    },
  )
}

export function getEnrollmentScoreDetail(enrollmentId: string | number) {
  return request.get<ApiResponse<ExamSubmissionDetail>>(
    `/api/exam/reviewer/scores/enrollments/${enrollmentId}/detail`,
    { timeout: 10000 },
  )
}

export interface ExamQuestion {
  id?: string
  campaignId?: string
  title?: string
  score?: number
  suggestedMinutes?: number | null
  taskScenario?: string
  taskRequirement?: string
  submissionDeliverable?: string
  attachmentUrl?: string | null
  attachmentName?: string | null
  attachmentSize?: number | null
  sortOrder?: number
}

export function listExamQuestions(campaignId: string | number) {
  return request.get<ApiResponse<ExamQuestion[]>>(
    `/api/exam/admin/campaigns/${campaignId}/questions`,
    {
      timeout: 10000,
    },
  )
}

export function createExamQuestion(
  campaignId: string | number,
  payload: Omit<ExamQuestion, 'id' | 'campaignId'>,
) {
  return request.post<ApiResponse<ExamQuestion>>(
    `/api/exam/admin/campaigns/${campaignId}/questions`,
    payload,
    {
      timeout: 10000,
    },
  )
}

export function updateExamQuestion(
  campaignId: string | number,
  questionId: string | number,
  payload: Omit<ExamQuestion, 'id' | 'campaignId'>,
) {
  return request.put<ApiResponse<ExamQuestion>>(
    `/api/exam/admin/campaigns/${campaignId}/questions/${questionId}`,
    payload,
    { timeout: 10000 },
  )
}

export function deleteExamQuestion(campaignId: string | number, questionId: string | number) {
  return request.delete<ApiResponse<void>>(
    `/api/exam/admin/campaigns/${campaignId}/questions/${questionId}`,
    {
      timeout: 10000,
    },
  )
}
