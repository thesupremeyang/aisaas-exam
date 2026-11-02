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
  return request.get<ApiResponse<ExamSubmission>>(candidatePath(submissionId), {
    silent: true,
    retry: 2,
  })
}

/** 查分模式：获取本人已提交作品关联的智能体配置，只读。 */
export function getCandidateAgentConfiguration(submissionId: string) {
  return request.get<ApiResponse<AgentDefinitionVO>>(`${candidatePath(submissionId)}/agent-config`, {
    timeout: 30000,
  })
}

/** 列出某报名下的全部作品（作品集） */
export function listSubmissions(enrollmentId: string | number) {
  return request.get<ApiResponse<ExamSubmission[]>>(
    `/api/exam/candidate/submissions/enrollments/${enrollmentId}/submissions`,
    { timeout: 30000, silent: true, retry: 2 },
  )
}

/** 在同一报名下新增一份草稿作品（绑定指定试题） */
export function createSubmission(enrollmentId: string | number, questionId?: string | number) {
  return request.post<ApiResponse<ExamSubmission>>(
    `/api/exam/candidate/submissions/enrollments/${enrollmentId}/submissions`,
    questionId ? { questionId } : {},
    { timeout: 30000 },
  )
}

/** 删除草稿作品（仅限本人、仅草稿） */
export function deleteSubmission(submissionId: string | number) {
  return request.delete<ApiResponse<boolean>>(candidatePath(String(submissionId)))
}

export function saveCandidateDraft(submissionId: string, payload: ExamSubmission) {
  // 草稿保存幂等，弱网可安全重试 1 次
  return request.put<ApiResponse<boolean>>(`${candidatePath(submissionId)}/draft`, payload, {
    retry: 1,
  })
}

export function submitCandidateWork(submissionId: string) {
  return request.post<ApiResponse<ExamSubmission>>(`${candidatePath(submissionId)}/submit`)
}

/** 评测失败时，使用当前已提交作品重新排队 AI 智评 */
export function retryCandidateAssessment(submissionId: string) {
  return request.post<ApiResponse<ExamSubmission>>(`${candidatePath(submissionId)}/retry-assessment`)
}

export function getAssessmentTask(submissionId: string) {
  return request.get<ApiResponse<AssessmentTask>>(`${candidatePath(submissionId)}/assessment-task`, {
    silent: true,
  })
}

export function getAssessmentReport(submissionId: string) {
  return request.get<ApiResponse<AssessmentReport>>(
    `${candidatePath(submissionId)}/assessment-report`,
    { silent: true },
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
  email?: string
  accountEnabled?: boolean
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
    timeout: 30000,
    silent: true,
  })
}

export function getCandidateProgress(campaignId: string | number) {
  return request.get<ApiResponse<CandidateProgress>>(`${candidatePortalPath}/progress`, {
    params: { campaignId },
    timeout: 30000,
    silent: true,
  })
}

/** 查分模式：获取当前考生本人的成绩与已提交作品入口。 */
export function getCandidateScore(campaignId: string | number) {
  return request.get<ApiResponse<ExamCandidateScore>>(`${candidatePortalPath}/score`, {
    params: { campaignId },
    timeout: 30000,
    silent: true,
  })
}

/** 考生端：获取指定批次的试题列表 */
export function listCandidateQuestions(campaignId: string | number) {
  return request.get<ApiResponse<ExamQuestion[]>>(`${candidatePortalPath}/questions`, {
    params: { campaignId },
    timeout: 30000,
    silent: true,
  })
}

export function getExamCandidateModelConfig(campaignId: string | number) {
  return request.get<ApiResponse<ExamCandidateModelConfig | null>>(
    `${candidatePortalPath}/model-config`,
    {
      params: { campaignId },
      timeout: 30000,
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
      timeout: 30000,
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
      timeout: 30000,
    },
  )
}

export function getExamCandidateStatus(campaignId: string | number) {
  return request.get<ApiResponse<ExamCandidateStatusSnapshot>>(
    `/api/exam/admin/campaigns/${campaignId}/candidates/status`,
    { timeout: 30000, silent: true },
  )
}

export interface ExamCandidateAccountCreateRequest {
  nickname: string
  username: string
  email?: string
  password: string
  organization?: string
}

export interface ExamCandidateAccountUpdateRequest {
  nickname: string
  username: string
  email?: string
  organization?: string
}

const examCandidateAdminPath = (campaignId: string | number) =>
  `/api/exam/admin/campaigns/${campaignId}/candidates`

export function createExamCandidateAccount(
  campaignId: string | number,
  payload: ExamCandidateAccountCreateRequest,
) {
  return request.post<ApiResponse<string | number>>(examCandidateAdminPath(campaignId), payload, {
    timeout: 30000,
  })
}

export function updateExamCandidateAccount(
  campaignId: string | number,
  enrollmentId: string | number,
  payload: ExamCandidateAccountUpdateRequest,
) {
  return request.put<ApiResponse<boolean>>(
    `${examCandidateAdminPath(campaignId)}/${enrollmentId}`,
    payload,
    { timeout: 30000 },
  )
}

export function changeExamCandidatePassword(
  campaignId: string | number,
  enrollmentId: string | number,
  password: string,
) {
  return request.put<ApiResponse<boolean>>(
    `${examCandidateAdminPath(campaignId)}/${enrollmentId}/password`,
    { password },
    { timeout: 30000 },
  )
}

export function resetExamCandidatePassword(
  campaignId: string | number,
  enrollmentId: string | number,
) {
  return request.post<ApiResponse<boolean>>(
    `${examCandidateAdminPath(campaignId)}/${enrollmentId}/reset-password`,
    null,
    { timeout: 30000 },
  )
}

export function updateExamCandidateAccountEnabled(
  campaignId: string | number,
  enrollmentId: string | number,
  enabled: boolean,
) {
  return request.patch<ApiResponse<boolean>>(
    `${examCandidateAdminPath(campaignId)}/${enrollmentId}/enabled`,
    null,
    { params: { enabled }, timeout: 30000 },
  )
}

export function removeExamCandidateAccount(
  campaignId: string | number,
  enrollmentId: string | number,
) {
  return request.delete<ApiResponse<boolean>>(
    `${examCandidateAdminPath(campaignId)}/${enrollmentId}`,
    { timeout: 30000 },
  )
}

export function batchResetExamCandidatePasswords(
  campaignId: string | number,
  enrollmentIds: Array<string | number>,
) {
  return request.post<ApiResponse<number>>(
    `${examCandidateAdminPath(campaignId)}/batch-reset-password`,
    { enrollmentIds },
    { timeout: 30000 },
  )
}

export function batchUpdateExamCandidateAccountsEnabled(
  campaignId: string | number,
  enrollmentIds: Array<string | number>,
  enabled: boolean,
) {
  return request.patch<ApiResponse<number>>(
    `${examCandidateAdminPath(campaignId)}/batch-enabled`,
    { enrollmentIds, enabled },
    { timeout: 30000 },
  )
}

export function batchRemoveExamCandidateAccounts(
  campaignId: string | number,
  enrollmentIds: Array<string | number>,
) {
  return request.post<ApiResponse<number>>(
    `${examCandidateAdminPath(campaignId)}/batch-remove`,
    { enrollmentIds },
    { timeout: 30000 },
  )
}

export interface ExamCandidateImportOutcome {
  rowNumber: number
  name?: string
  username?: string
  success: boolean
  errorMessage?: string
}

export interface ExamCandidateImportResult {
  totalRows: number
  successCount: number
  failureCount: number
  outcomes: ExamCandidateImportOutcome[]
}

/** 下载考生批量导入 Excel 模板（blob）。 */
export function downloadCandidateImportTemplate(campaignId: string | number) {
  return request.get(`${examCandidateAdminPath(campaignId)}/import-template`, {
    responseType: 'blob',
    timeout: 30000,
  })
}

/** 上传 Excel 名单批量创建考生，逐行返回导入结果。 */
export function importExamCandidates(campaignId: string | number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<ApiResponse<ExamCandidateImportResult>>(
    `${examCandidateAdminPath(campaignId)}/import`,
    formData,
    { timeout: 600000 },
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
    timeout: 30000,
  })
}

export function updateExamCampaignSchedule(
  campaignId: string | number,
  payload: ExamCampaignScheduleRequest,
) {
  return request.put<ApiResponse<ExamCampaign>>(
    `/api/exam/admin/campaigns/${campaignId}/schedule`,
    payload,
    { timeout: 30000 },
  )
}

export function createExamCampaign(payload: ExamCampaignUpdateRequest) {
  return request.post<ApiResponse<ExamCampaign>>('/api/exam/admin/campaigns', payload, {
    timeout: 30000,
  })
}

export function updateExamCampaign(
  campaignId: string | number,
  payload: ExamCampaignUpdateRequest,
) {
  return request.put<ApiResponse<ExamCampaign>>(
    `/api/exam/admin/campaigns/${campaignId}`,
    payload,
    { timeout: 30000 },
  )
}

export function updateExamCampaignEnabled(campaignId: string | number, enabled: boolean) {
  return request.patch<ApiResponse<ExamCampaign>>(
    `/api/exam/admin/campaigns/${campaignId}/enabled`,
    null,
    { params: { enabled }, timeout: 30000 },
  )
}

export function updateExamCampaignScoreQuery(campaignId: string | number, enabled: boolean) {
  return request.patch<ApiResponse<ExamCampaign>>(
    `/api/exam/admin/campaigns/${campaignId}/score-query`,
    null,
    { params: { enabled }, timeout: 30000 },
  )
}

export function startExamNow(campaignId: string | number) {
  return request.post<ApiResponse<ExamCampaign>>(
    `/api/exam/admin/campaigns/${campaignId}/start-now`,
    null,
    { timeout: 30000 },
  )
}

export function listCandidateAgents(campaignId: string | number) {
  return request.get<ApiResponse<ExamAgentSummaryVO[]>>(`${candidatePortalPath}/agents`, {
    params: { campaignId },
    timeout: 30000,
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
      timeout: 30000,
    },
  )
}

export function consumeSsoTicket(ticket: string) {
  return request.post<ApiResponse<ExamSsoTicketConsumeVO>>(
    `/api/exam/sso/tickets/${encodeURIComponent(ticket)}/consume`,
    undefined,
    { timeout: 30000 },
  )
}

// ============ 评审端：学员 AI 阅卷分数 ============

export interface ExamScoreRow {
  enrollmentId?: string | number
  submissionId?: string | number
  candidateName?: string
  identityNumber?: string
  phone?: string
  loginName?: string
  organization?: string
  tenantName?: string
  /** 考生租户 ID，用于管理员进入考生配置空间 */
  tenantId?: string | number
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
    timeout: 30000,
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
  comment?: string,
) {
  return request.put<ApiResponse<boolean>>(`/api/exam/reviewer/scores/${submissionId}`, null, {
    params: { finalScore, comment },
    timeout: 30000,
  })
}

export interface ExamScorePublishResult {
  campaignId?: string
  enrollmentCount?: number
  publishedCount?: number
  skippedCount?: number
  pendingReviewCount?: number
  scoreQueryEnabled?: boolean
}

/** 发布成绩：先把已复核的本题分固化为实操分，再开启查分；force 为 true 时允许未复核完也发布 */
export function publishCampaignScores(campaignId: string | number, force = false) {
  return request.post<ApiResponse<ExamScorePublishResult>>(
    `/api/exam/reviewer/scores/campaigns/${campaignId}/publish`,
    null,
    { params: { force }, timeout: 60000 },
  )
}

/** 录入（或清空）考生理论分，满分 100 */
export function saveTheoryScore(enrollmentId: string | number, score?: number | null) {
  return request.put<ApiResponse<boolean>>(
    `/api/exam/reviewer/scores/enrollments/${enrollmentId}/theory-score`,
    null,
    { params: { score }, timeout: 30000 },
  )
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
  /** AI 阅卷原始分（配置量表） */
  aiRawScore?: number
  /** AI 阅卷原始分满分（该题配置可评分项满分） */
  aiMaxScore?: number
  rawScoreLimit?: number
  dimensionScores?: Record<string, number>
  confidence?: string
  modelName?: string
  aiScoredAt?: string
  conclusion?: string
  evidence?: SubmissionDimEvidence[]
  risks?: string[]
  suggestions?: string[]
  /** AI 阅卷报告：分项评分（含满分与评分依据） */
  dimensionItems?: GradingDimItem[]
  /** AI 阅卷报告：面向考生的通俗评语 */
  plainComment?: string
  /** AI 阅卷报告：设计清单命中条数 */
  checklistHits?: number
  /** AI 阅卷报告：设计清单逐条命中说明 */
  checklistDetail?: string[]
  /** AI 阅卷报告：完整性 / 真实性标记 */
  integrityFlags?: string[]
  /** AI 阅卷报告：主导权（疑似外包）判定状态 */
  dominanceStatus?: string
  /** AI 阅卷报告：主导权判定通道明细（A~E） */
  dominanceChannels?: GradingChannel[]
  /** AI 阅卷报告：主导权封顶比例与封顶原因 */
  capRatio?: number
  capStatus?: string
  /** AI 阅卷报告：额外扣分与扣分原因 */
  deductionAmount?: number
  deductionReason?: string
  finalScore?: number
  finalRawScore?: number
  reviewDimensionScores?: Record<string, number>
  reviewComment?: string
  reviewed?: boolean
}

/** AI 阅卷报告的单个评分项 */
export interface GradingDimItem {
  dimension?: string
  score?: number
  max?: number
  evidence?: string
}

/** AI 阅卷报告的主导权判定通道 */
export interface GradingChannel {
  channel?: string
  finding?: string
  evidence?: string
}

export interface ExamSubmissionDetail {
  enrollmentId?: string | number
  submissionId?: number
  candidateName?: string
  identityNumber?: string
  loginName?: string
  organization?: string
  tenantName?: string
  /** 考生租户 ID，用于管理员进入考生配置空间 */
  tenantId?: string | number
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
  /** 理论成绩（满分 100），管理员录入 */
  theoryScore?: number | null
  /** 实操成绩（各题复核得分之和），发布时固化 */
  practicalScore?: number | null
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
      timeout: 30000,
    },
  )
}

export function getEnrollmentScoreDetail(enrollmentId: string | number) {
  return request.get<ApiResponse<ExamSubmissionDetail>>(
    `/api/exam/reviewer/scores/enrollments/${enrollmentId}/detail`,
    { timeout: 30000 },
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
      timeout: 30000,
    },
  )
}

/** 管理端答卷列表行（答卷管理页） */
export interface ExamSubmissionAdminRow {
  submissionId?: string | number
  enrollmentId?: string | number
  /** 考生所属租户 ID（管理员进入考生智能体配置界面用） */
  tenantId?: string | number
  candidateName?: string
  candidateUsername?: string
  organization?: string
  questionId?: string | number
  questionTitle?: string
  questionScore?: number
  agentId?: string | number
  agentName?: string
  workType?: string
  status?: string
  currentStep?: number
  submittedAt?: string
}

/** 管理端答卷列表：按批次拉取全部答卷（含考生/题目/智能体信息） */
export function listExamSubmissionsForAdmin(campaignId: string | number, name?: string) {
  return request.get<ApiResponse<ExamSubmissionAdminRow[]>>(
    `/api/exam/admin/campaigns/${campaignId}/submissions`,
    {
      params: name ? { name } : undefined,
      timeout: 30000,
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
      timeout: 30000,
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
    { timeout: 30000 },
  )
}

export function deleteExamQuestion(campaignId: string | number, questionId: string | number) {
  return request.delete<ApiResponse<void>>(
    `/api/exam/admin/campaigns/${campaignId}/questions/${questionId}`,
    {
      timeout: 30000,
    },
  )
}

/* ===================== AI 阅卷（配置评分） ===================== */

/** AI 阅卷单个题目评分格 */
export interface GradingQuestionCell {
  questionId?: string | number
  questionNo?: number
  questionTitle?: string
  /** 题目原始满分（exam_question.score） */
  questionScore?: number
  submissionId?: string | number
  agentId?: string | number
  agentName?: string
  submissionStatus?: string
  /** QUEUED / RUNNING / SUCCEEDED / FAILED / 未提交 / 无任务 */
  taskStatus?: string
  taskErrorMessage?: string
  graded?: boolean
  totalScore?: number
  configMaxScore?: number
  dominanceStatus?: string
  capStatus?: string
  confidence?: string
  modelName?: string
  integrityFlags?: string[]
  gradedAt?: string
}

/** AI 阅卷按考生聚合的行 */
export interface GradingCandidateRow {
  enrollmentId?: string | number
  candidateName?: string
  loginName?: string
  organization?: string
  tenantName?: string
  questionCount?: number
  submittedCount?: number
  questions?: GradingQuestionCell[]
  totalScore?: number
  /** 考试满分（各题分值之和） */
  totalMaxScore?: number
}

/** AI 阅卷总览 */
export interface GradingOverview {
  campaignId?: string | number
  campaignName?: string
  taskCounts?: Record<string, number>
  rows?: GradingCandidateRow[]
}

/** AI 阅卷分项 */
export interface GradingItem {
  name?: string
  score?: number
  max?: number
  evidence?: string
}

/** AI 阅卷单卷详情 */
export interface GradingDetail {
  submissionId?: string | number
  questionId?: string | number
  questionNo?: number
  questionTitle?: string
  questionScore?: number
  enrollmentId?: string | number
  candidateName?: string
  loginName?: string
  agentId?: string | number
  agentName?: string
  submissionStatus?: string
  taskStatus?: string
  taskErrorMessage?: string
  baseScore?: number
  coefficient?: number
  checklistHits?: number
  checklistDetail?: string[]
  dominanceStatus?: string
  dominanceChannels?: { channel?: string; finding?: string; evidence?: string }[]
  items?: GradingItem[]
  capStatus?: string
  capRatio?: number
  deductionAmount?: number
  deductionReason?: string
  totalScore?: number
  configMaxScore?: number
  confidence?: string
  /** 通俗评语（面向非技术读者） */
  plainComment?: string
  /** 技术版评分摘要（管理员核查用） */
  summary?: string
  integrityFlags?: string[]
  modelName?: string
  rubricVersion?: string
  gradedAt?: string
}

/** 按场次批量开始 AI 阅卷（跳过已有进行中任务的答卷） */
export function startExamGrading(campaignId: string | number) {
  return request.post<ApiResponse<Record<string, number>>>(
    `/api/exam/admin/grading/campaigns/${campaignId}/run`,
    {},
    { timeout: 60000 },
  )
}

/** 单卷重新评分 */
export function rerunExamGrading(submissionId: string | number) {
  return request.post<ApiResponse<boolean>>(
    `/api/exam/admin/grading/submissions/${submissionId}/rerun`,
    {},
    { timeout: 30000 },
  )
}

/** AI 阅卷总览（进度统计 + 按考生分组评分行） */
export function getExamGradingOverview(campaignId: string | number) {
  return request.get<ApiResponse<GradingOverview>>(
    `/api/exam/admin/grading/campaigns/${campaignId}/overview`,
    { timeout: 60000 },
  )
}

/** AI 阅卷单卷详情（分项明细 + 主导权判定 + 理由） */
export function getExamGradingDetail(submissionId: string | number) {
  return request.get<ApiResponse<GradingDetail>>(
    `/api/exam/admin/grading/submissions/${submissionId}/detail`,
    { timeout: 30000 },
  )
}

/** 采纳 AI 阅卷分为复核得分 */
export function adoptExamGrading(submissionId: string | number) {
  return request.post<ApiResponse<boolean>>(
    `/api/exam/admin/grading/submissions/${submissionId}/adopt`,
    {},
    { timeout: 30000 },
  )
}

/* ===================== AI 阅卷模型设置 ===================== */

export interface GradingModelSettings {
  endpoint?: string
  model?: string
  apiKeyMasked?: string
  apiKeyConfigured?: boolean
  enabled?: boolean
}

export interface GradingTestResult {
  success?: boolean
  latencyMs?: number
  message?: string
}

/** 读取 AI 阅卷模型配置（Key 脱敏） */
export function getGradingModelSettings() {
  return request.get<ApiResponse<GradingModelSettings>>(
    '/api/exam/admin/grading/settings',
    { timeout: 30000 },
  )
}

/** 连通性测试（不落库）；apiKey 留空表示沿用已保存的 Key */
export function testGradingModelSettings(data: { endpoint: string; model: string; apiKey?: string }) {
  return request.post<ApiResponse<GradingTestResult>>(
    '/api/exam/admin/grading/settings/test',
    data,
    { timeout: 60000 },
  )
}

/** 保存模型配置（后端保存前强制连通性测试，失败不落库） */
export function saveGradingModelSettings(data: {
  endpoint: string
  model: string
  apiKey?: string
  enabled: boolean
}) {
  return request.post<ApiResponse<GradingTestResult & { saved?: boolean; apiKeyMasked?: string }>>(
    '/api/exam/admin/grading/settings',
    data,
    { timeout: 60000 },
  )
}
