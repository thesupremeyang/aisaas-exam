/**
 * 路由常量
 *
 * @author huxuehao
 */

/**
 * 路由名称常量
 */
export const RouteNames = {
  // 认证相关
  LOGIN: 'Login',
  REGISTER: 'Register',
  FORGOT_PASSWORD: 'ForgotPassword',
  PREVIEW: 'Preview',

  // 布局
  LAYOUT: 'Layout',

  // 主页
  HOME: 'Home',
  DASHBOARD: 'Dashboard',

  // Agent管理
  AGENT: 'Agent',

  // 考试门户
  EXAM_PORTAL: 'ExamPortal',
  EXAM_AGENT_GATEWAY: 'ExamAgentGateway',
  CANDIDATE_SUBMISSION: 'CandidateSubmission',
  CANDIDATE_ASSESSMENT: 'CandidateAssessment',
  REVIEWER_ASSESSMENT: 'ReviewerAssessment',
  EXAM_ADMIN: 'ExamAdmin',
  EXAM_QUESTIONS: 'ExamQuestions',
  EXAM_SUBMISSIONS: 'ExamSubmissions',
  EXAM_CANDIDATE_STATUS: 'ExamCandidateStatus',
  EXAM_GRADING: 'ExamGrading',
  USER_MANAGE: 'UserManage',

  // Agent广场
  EXPLORE: 'Explore',

  // 模型管理
  MODEL: 'Model',
  MODEL_PROVIDER_CONFIG: 'ModelProviderConfig',

  // 提示词管理
  PROMPT: 'Prompt',

  // 知识库管理
  KNOWLEDGE: 'Knowledge',

  // 工具管理
  TOOL: 'Tool',

  // Hook管理
  HOOK: 'Hook',

  // 技能包管理
  SKILL: 'Skill',
  SKILL_EDITOR: 'SkillEditor',
  SKILL_EDITOR_NEW: 'SkillEditorNew',

  // MCP管理
  MCP: 'Mcp',

  // 敏感词管理
  SENSITIVE: 'Sensitive',

  // 账号管理
  ACCOUNT: 'Account',

  // 系统管理
  SYSTEM: 'System',
  CREATOR_ADMIN: 'CreatorAdmin',

  // 智能体对话
  CHAT: 'Chat',
  CHAT_HISTORY: 'ChatHistory',
  COMMUNICATION: 'Communication',

  // 错误页面
  NOT_FOUND: 'NotFound',
} as const

/**
 * 路由路径常量
 */
export const RoutePaths = {
  // 认证相关
  LOGIN: '/login',
  REGISTER: '/register',
  FORGOT_PASSWORD: '/forgot-password',
  PREVIEW: '/preview',

  // 主页
  ROOT: '/',
  HOME: '/home',
  DASHBOARD: '/dashboard',

  // Agent管理
  AGENT: 'agent',

  // 考试门户
  EXAM_PORTAL: 'exam/portal',
  EXAM_AGENT_GATEWAY: 'exam/create-agent',
  CANDIDATE_SUBMISSION: 'exam/candidate/submissions/:submissionId',
  CANDIDATE_ASSESSMENT: 'exam/candidate/submissions/:submissionId/assessment',
  REVIEWER_ASSESSMENT: 'exam/reviewer/submissions/:submissionId/review',
  EXAM_ADMIN: 'exam/admin/campaigns',
  EXAM_QUESTIONS: 'exam/admin/questions',
  EXAM_SUBMISSIONS: 'exam/admin/submissions',
  EXAM_CANDIDATE_STATUS: 'exam/admin/candidates',
  EXAM_GRADING: 'exam/admin/grading',
  USER_MANAGE: 'account',

  // Agent广场
  EXPLORE: 'explore',

  // 模型管理
  MODEL: 'model',
  MODEL_PROVIDER_CONFIG: '/model/:providerId/config',

  // 提示词管理
  PROMPT: 'prompt',

  // 知识库管理
  KNOWLEDGE: 'knowledge',

  // 知识库文档管理
  KNOWLEDGE_DOCUMENTS: 'knowledge/:id/documents',

  // 工具管理
  TOOL: 'tool',

  // Hook管理
  HOOK: 'hook',

  // 技能包管理
  SKILL: 'skill',
  SKILL_NEW: 'skill/new',
  SKILL_EDIT: 'skill/:id/edit',

  // MCP管理
  MCP: 'mcp',

  // 敏感词管理
  SENSITIVE: 'sensitive',

  // 账号管理
  ACCOUNT: '/account',
  CREATOR_ADMIN: 'xiaotu-config',

  // 智能体对话
  CHAT: '/chat',
  CHAT_HISTORY: '/chat/history',
  COMMUNICATION: '/communication',

  // 错误页面
  NOT_FOUND: '/:pathMatch(.*)*',
  FORBIDDEN: '/403',
  SERVER_ERROR: '/500',
} as const

/**
 * 白名单路由（无需认证）
 */
export const WHITE_LIST = [
  RoutePaths.LOGIN,
  RoutePaths.REGISTER,
  RoutePaths.FORGOT_PASSWORD,
  RoutePaths.PREVIEW,
]

/**
 * 重定向路由
 */
export const REDIRECT_ROUTE_NAME = 'Redirect'
