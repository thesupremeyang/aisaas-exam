/**
 * 公共页面路由
 *
 * @author huxuehao
 */

import type { AppRouteRecordRaw } from '../types'
import { RouteNames, RoutePaths } from '../constants'
import { isSidePortal } from '@/config/portal'

/**
 * 业务路由配置
 *
 * 根布局根据环境变量 VITE_UI_PORTAL 选择：
 *   - v1：原顶部水平菜单（layout/index.vue）
 *   - v2：左侧 Sider + 顶部 sub-header（layout-v2/index.vue）
 */
const bizRoutes: AppRouteRecordRaw[] = [
  // 考试门户：独立全屏布局（不渲染 Sider/Header），登录后默认进入此页
  ...(isSidePortal
    ? [
        {
          path: '/exam/create-agent',
          component: () => import('@/layout-v2/ExamPortalLayout.vue'),
          children: [
            {
              path: '',
              name: RouteNames.EXAM_AGENT_GATEWAY,
              component: () => import('@/views/exam/AgentGatewayPage.vue'),
            },
          ],
          meta: { hidden: true, hideFooter: true, fullPage: true },
        } as AppRouteRecordRaw,
        {
          path: '/exam/portal',
          component: () => import('@/layout-v2/ExamPortalLayout.vue'),
          children: [
            {
              path: '',
              name: RouteNames.EXAM_PORTAL,
              component: () => import('@/views/exam/ExamPortalPage.vue'),
              meta: {
                title: '考试门户',
                hidden: false,
                hideFooter: true,
                fullPage: true,
              },
            } as AppRouteRecordRaw,
          ],
          meta: {
            title: '考试门户',
            hidden: false,
            hideFooter: true,
            fullPage: true,
          },
        } as AppRouteRecordRaw,
      ]
    : []),
  // 提交作品：独立全屏布局（不渲染 Sider/Header），与考试门户一致
  {
    path: '/exam/submission/:submissionId',
    component: () => import('@/layout-v2/ExamPortalLayout.vue'),
    children: [
      {
        path: '',
        name: RouteNames.CANDIDATE_SUBMISSION,
        component: () => import('@/views/exam/CandidateSubmissionPage.vue'),
        props: (route) => ({ submissionId: route.params.submissionId as string }),
        meta: {
          title: '提交智能体作品',
          hidden: true,
          hideFooter: true,
          fullPage: true,
        },
      } as AppRouteRecordRaw,
    ],
    meta: {
      title: '提交智能体作品',
      hidden: true,
      hideFooter: true,
      fullPage: true,
    },
  } as AppRouteRecordRaw,
  // AI 智评报告：独立全屏布局（不渲染 Sider/Header），与考试门户一致
  {
    path: '/exam/candidate/submissions/:submissionId/assessment',
    component: () => import('@/layout-v2/ExamPortalLayout.vue'),
    children: [
      {
        path: '',
        name: RouteNames.CANDIDATE_ASSESSMENT,
        component: () => import('@/views/exam/CandidateAssessmentPage.vue'),
        props: (route) => ({ submissionId: route.params.submissionId as string }),
        meta: {
          title: 'AI 智评报告',
          hidden: true,
          hideFooter: true,
          fullPage: true,
        },
      } as AppRouteRecordRaw,
    ],
    meta: {
      title: 'AI 智评报告',
      hidden: true,
      hideFooter: true,
      fullPage: true,
    },
  } as AppRouteRecordRaw,
  {
    path: '/',
    // 按门户版本动态加载布局组件，运行时已固化（vite 编译时常量）
    component: isSidePortal
      ? () => import('@/layout-v2/index.vue')
      : () => import('@/layout/index.vue'),
    children: [
      {
        path: RoutePaths.CREATOR_ADMIN,
        name: RouteNames.CREATOR_ADMIN,
        component: () => import('@/views/System/CreatorAgentConfig.vue'),
        meta: {
          title: '小图系统配置',
          hidden: true,
          hideFooter: true,
        },
      },
      {
        path: 'reviewer-scores',
        name: 'ReviewerScores',
        component: () => import('@/views/exam/ReviewerScoreListPage.vue'),
        meta: {
          title: '成绩与发布',
          hidden: true,
          hideFooter: true,
        },
      },
      {
        path: RoutePaths.EXAM_ADMIN,
        name: RouteNames.EXAM_ADMIN,
        component: () => import('@/views/exam/ExamCampaignAdminPage.vue'),
        meta: {
          title: '考试场次',
          hidden: false,
          hideFooter: true,
        },
      },
      {
        path: RoutePaths.EXAM_QUESTIONS,
        name: RouteNames.EXAM_QUESTIONS,
        component: () => import('@/views/exam/ExamQuestionAdminPage.vue'),
        meta: {
          title: '试题管理',
          hidden: false,
          hideFooter: true,
        },
      },
      {
        path: RoutePaths.EXAM_SUBMISSIONS,
        name: RouteNames.EXAM_SUBMISSIONS,
        component: () => import('@/views/exam/ExamSubmissionAdminPage.vue'),
        meta: {
          title: '答卷管理',
          hidden: false,
          hideFooter: true,
        },
      },
      {
        path: RoutePaths.EXAM_GRADING,
        name: RouteNames.EXAM_GRADING,
        component: () => import('@/views/exam/ExamGradingAdminPage.vue'),
        meta: {
          title: 'AI 阅卷',
          hidden: false,
          hideFooter: true,
        },
      },
      {
        path: RoutePaths.USER_MANAGE,
        name: RouteNames.USER_MANAGE,
        component: () => import('@/views/Account/UserManagePage.vue'),
        meta: {
          title: '用户管理',
          hidden: false,
          hideFooter: true,
        },
      },
      {
        path: RoutePaths.EXAM_CANDIDATE_STATUS,
        name: RouteNames.EXAM_CANDIDATE_STATUS,
        component: () => import('@/views/exam/ExamCandidateStatusAdminPage.vue'),
        meta: {
          title: '考生管理',
          hidden: false,
          hideFooter: true,
        },
      },
      {
        path: RoutePaths.SENSITIVE,
        name: RouteNames.SENSITIVE,
        component: () => import('@/views/Sensitive/index.vue'),
        meta: {
          title: '敏感词',
          hidden: false,
        },
      },
      {
        path: RoutePaths.PROMPT,
        name: RouteNames.PROMPT,
        component: () => import('@/views/Prompt/index.vue'),
        meta: {
          title: '系统提示词模版',
          hidden: false,
        },
      },
      {
        path: RoutePaths.MODEL,
        name: RouteNames.MODEL,
        component: () => import('@/views/Model/index.vue'),
        meta: {
          title: '模型供应商',
          hidden: false,
        },
      },
      {
        path: RoutePaths.MODEL_PROVIDER_CONFIG,
        name: RouteNames.MODEL_PROVIDER_CONFIG,
        component: () => import('@/views/Model/ProviderConfig.vue'),
        meta: {
          title: '模型配置',
          hidden: true,
        },
      },

      {
        path: RoutePaths.AGENT,
        name: RouteNames.AGENT,
        component: () => import('@/views/Agent/index.vue'),
        meta: {
          title: '智能体',
          hidden: false,
        },
      },
      {
        path: RoutePaths.EXPLORE,
        name: RouteNames.EXPLORE,
        component: () => import('@/views/Explore/index.vue'),
        meta: {
          title: '智能体广场',
          hidden: false,
        },
      },
      {
        path: RoutePaths.REVIEWER_ASSESSMENT,
        name: RouteNames.REVIEWER_ASSESSMENT,
        component: () => import('@/views/exam/ReviewerAssessmentPage.vue'),
        props: true,
        meta: {
          title: '评委复核',
          hidden: true,
          hideFooter: true,
        },
      },
      {
        path: RoutePaths.HOOK,
        name: RouteNames.HOOK,
        component: () => import('@/views/Hook/index.vue'),
        meta: {
          title: '钩子',
          hidden: false,
        },
      },
      {
        path: RoutePaths.TOOL,
        name: RouteNames.TOOL,
        component: () => import('@/views/Tool/index.vue'),
        meta: {
          title: '工具',
          hidden: false,
        },
      },
      {
        path: RoutePaths.SKILL,
        name: RouteNames.SKILL,
        component: () => import('@/views/Skill/index.vue'),
        meta: {
          title: '技能包',
          hidden: false,
        },
      },
      {
        path: RoutePaths.SKILL_NEW,
        name: RouteNames.SKILL_EDITOR_NEW,
        component: () => import('@/views/Skill/SkillEditorView.vue'),
        meta: {
          title: '新建技能包',
          hidden: true,
          hideFooter: true,
        },
      },
      {
        path: RoutePaths.SKILL_EDIT,
        name: RouteNames.SKILL_EDITOR,
        component: () => import('@/views/Skill/SkillEditorView.vue'),
        meta: {
          title: '编辑技能包',
          hidden: true,
          hideFooter: true,
        },
      },
      {
        path: RoutePaths.MCP,
        name: RouteNames.MCP,
        component: () => import('@/views/Mcp/index.vue'),
        meta: {
          title: 'MCP',
          hidden: false,
        },
      },
      {
        path: RoutePaths.KNOWLEDGE,
        name: RouteNames.KNOWLEDGE,
        component: () => import('@/views/Knowledge/index.vue'),
        meta: {
          title: '知识库',
          hidden: false,
        },
      },
    ],
  },
]

export default bizRoutes
