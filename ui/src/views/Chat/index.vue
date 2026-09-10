<script setup lang="ts">
import {computed, defineAsyncComponent, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Modal, message } from 'ant-design-vue'
import { useAccountStore, useChatStore } from '@/stores'
import { RouteNames } from '@/router/constants'
import { formatSessionTitle } from '@/utils/chat/format'
import { useAgentDetail } from '@/composables/chat/useAgentDetail'
import { useSessions } from '@/composables/chat/useSessions'
import { useCurrentSession } from '@/composables/chat/useCurrentSession'
import { useChatStream } from '@/composables/chat/useChatStream'
import ChatSidebar from '@/components/chat/ChatSidebar.vue'
import ChatMain from '@/components/chat/ChatMain.vue'
import RenameModal from '@/components/chat/RenameModal.vue'
import WorkspacePanel from '@/components/workspace/WorkspacePanel.vue'
import type { AccountVO, DisplayMessage, ChatMessageVO, UploadedFileItem, ChatSessionVO } from '@/types'
import * as chatSessionApi from '@/api/chatSession'
import * as agentApi from '@/api/agent'
import * as modelApi from '@/api/model'
import { getActiveRuns, getStatus } from '@/api/agui'
import {
  ArrowRightOutlined,
  CheckCircleOutlined,
  CloseOutlined,
  EditOutlined,
  LoadingOutlined,
  MessageOutlined,
} from '@ant-design/icons-vue'
import {
  buildUserTextFromPayload,
  injectSubmissionToRawContent
} from '@/utils/chat/uip.ts'
import type { InteractionSubmitPayload } from '@/components/markdown/uip/types'
import {
  findCreatorConfirmationIndex,
  extractCreatorResultRows,
  extractCreatedAgentFromMessages,
  mergeCreatorResultRows,
  isCreatorBuildTimedOut,
  isCreatorAwaitingConfirmation,
  shouldPollCreatorRunStatus,
  type CreatorResultRow,
} from './creatorStepGuard'

const CreatorHaloVisual = defineAsyncComponent(
  () => import('@/components/chat/CreatorHaloVisual.vue'),
)

const props = withDefaults(defineProps<{
  showAccount: boolean
  chatAgentId: string | null | undefined
  externalUserInfo?: AccountVO | null
  compact?: boolean
  initialPrompt?: string
  portalAgent?: boolean
  welcomeHeadline?: string
  welcomeDescription?: string
  starterQuestionsOverride?: string[]
}>(), {
  showAccount: true,
  chatAgentId: null,
  externalUserInfo: null,
  compact: false,
  initialPrompt: '',
  portalAgent: false,
})

const route = useRoute()
const router = useRouter()
const accountStore = useAccountStore()
const chatStore = useChatStore()
const userInfo = computed(() => props.externalUserInfo ?? accountStore.userInfo)

const agentId = computed(() => (props.chatAgentId || route.params.agentId) as string || '')
const starterQuestions = computed(() => props.compact
  ? (props.starterQuestionsOverride?.length ? props.starterQuestionsOverride : [
      '我想做一个帮销售自动整理客户跟进记录的智能体',
      '我只有一个模糊想法，你可以一步步帮我梳理吗？',
      '帮我创建一个能审核合同风险并给出修改建议的智能体',
    ])
  : [])

// 智能体详情
const { agentDetail, allowFileType } = useAgentDetail(agentId, false, props.portalAgent)

// 记忆/规划是否可用（由 agentDetail 决定）
const accountId = computed(() => userInfo.value?.id)
const enableMemory = computed(() => agentDetail.value?.enableMemory === true)
const enablePlanning = computed(() => agentDetail.value?.enablePlanning === true)
const showToolProcess = computed(() => !props.compact && agentDetail.value?.showToolProcess === true)
// 是否配置了代码执行
const hasCodeExecutionConfig = computed(() => agentDetail.value?.codeExecutionConfigId)

// 记忆/规划/侧边栏状态：从 Pinia store 读取（持久化由 pinia-plugin-persistedstate 处理）
const memoryActive = computed(() => {
  const id = agentDetail.value?.id ?? agentId.value
  chatStore.preferences // 依赖以保持响应性
  return chatStore.getMemoryActive(id as string, accountId.value as string, enableMemory.value)
})
const planActive = computed(() => {
  const id = agentDetail.value?.id ?? agentId.value
  chatStore.preferences
  return chatStore.getPlanActive(id as string, accountId.value as string, enablePlanning.value)
})
const toolProcessActive = computed(() => {
  const id = agentDetail.value?.id ?? agentId.value
  chatStore.preferences
  return chatStore.getToolProcessActive(id as string, accountId.value as string, showToolProcess.value)
})
const sidebarCollapsed = computed({
  get: () => {
    const id = agentDetail.value?.id ?? agentId.value
    chatStore.preferences
    return chatStore.getSidebarCollapsed(id as string, accountId.value as string)
  },
  set: (v: boolean) => {
    const id = agentDetail.value?.id ?? agentId.value
    chatStore.setSidebarCollapsed(id as string, accountId.value as string, v)
  },
})

const handleMemoryChange = (v: boolean) => {
  const id = agentDetail.value?.id ?? agentId.value
  chatStore.setMemoryActive(id as string, accountId.value as string, v)
}

const handlePlanChange = (v: boolean) => {
  const id = agentDetail.value?.id ?? agentId.value
  chatStore.setPlanActive(id as string, accountId.value as string, v)
}

const handelToolProcess = (v: boolean) => {
  const id = agentDetail.value?.id ?? agentId.value
  chatStore.setToolProcessActive(id as string, accountId.value as string, v)
}

// 会话列表管理
const {
  pinnedSessions,
  otherSessions,
  loading: sessionsLoading,
  hasMore: sessionsHasMore,
  createSession,
  updateSessionTitle,
  pinSession,
  unpinSession,
  deleteSession,
  loadSessions,
  loadMoreSessions,
} = useSessions(agentId)

// 当前会话管理
const {
  currentSessionId,
  currentSessionTitle,
  currentSessionMessageTable,
  messagesList,
  hasMoreHistory,
  historyLoading,
  selectSession,
  resetSession,
  loadCurrentMessages,
  loadMoreHistory,
  loadingMessages
} = useCurrentSession(agentId)

// 已上传附件（仅已完成上传的计入 fileIds）
const uploadedFiles = ref<UploadedFileItem[]>([])
const fileIds = computed(() =>
  uploadedFiles.value.filter((f) => !f.uploading).map((f) => f.id)
)

// 流式对话及工具调用
const {
  agentHasResult,
  streamingContent,
  streamingMessageId,
  streamingRole,
  toolCallsInProgress,
  isRunning,
  currentPlan,
  sendMessage,
  sendToolContent,
  abortRun,
  reconnect: reconnectStream,
  disconnect: disconnectStream,
  resetStreamingState,
} = useChatStream(
  agentId,
  agentDetail,
  currentSessionId,
  fileIds,
  memoryActive,
  planActive,
  toolProcessActive,
  (chatMsg: ChatMessageVO) => {
    messagesList.value.push(chatMsg)
  },
  userInfo,
)

// 输入框内容
const inputText = ref('')
const initialPromptSent = ref(false)
const creatorRootRef = ref<HTMLElement | null>(null)
const creatorHistoryOpen = ref(false)
const creatorProgress = ref(0)
const creatorBuildLocked = ref(false)
const portalV2EnabledByThisChat = ref(false)
const creatorBuildTimedOut = ref(false)
let creatorProgressTimer: ReturnType<typeof setInterval> | null = null
let creatorBuildStartedAt: number | null = null
const CREATOR_BUILD_TIMEOUT_MS = 180_000

// 记录最近一次流式消息的 ID，用于 DOM key 桥接，避免流式→保存切换时的闪烁
const lastStreamingKey = ref<string | null>(null)

watch(streamingMessageId, (newId) => {
  if (newId) {
    lastStreamingKey.value = newId
  }
})

const creatorConfirmationIndex = computed(() => {
  if (!props.compact) return -1
  return findCreatorConfirmationIndex(messagesList.value)
})

const creatorAssistantText = computed(() =>
  messagesList.value
    .filter(item => item?.role === 'assistant')
    .map(item => item.content || '')
    .join('\n'),
)
const creatorBuildStatusText = computed(() =>
  messagesList.value
    .filter(item => item?.role === 'assistant' || item?.role === 'error')
    .map(item => item.content || '')
    .join('\n'),
)

const creatorCompleted = computed(() =>
  /创建完成|创建成功|成功创建|创建并启用|已添加到.*智能体|智能体\s*ID/i.test(creatorAssistantText.value),
)

const creatorFailed = computed(() =>
  /创建失败|创建超时|执行失败|部署失败|验证失败|SecurityError|No static resource/i.test(creatorBuildStatusText.value),
)

const createdAgentResult = computed(() =>
  extractCreatedAgentFromMessages(messagesList.value),
)

const creatorResultText = computed(() =>
  [...messagesList.value]
    .reverse()
    .find(item => item?.role === 'assistant' && /创建完成|创建成功|智能体\s*(?:ID|编号)/i.test(item.content || ''))
    ?.content || '',
)
const creatorDetailRows = ref<CreatorResultRow[]>([])

const creatorHistorySessions = computed(() => [
  ...pinnedSessions.value,
  ...otherSessions.value,
])

const autoInitialPrompt = computed(() => {
  const queryPrompt = typeof route.query.initialPrompt === 'string'
    ? route.query.initialPrompt
    : ''
  return props.initialPrompt?.trim() || queryPrompt.trim()
})

const creatorAwaitingConfirmation = computed(() => {
  if (!props.compact || creatorBuildLocked.value || creatorConfirmationIndex.value >= 0) return false
  let lastAssistantIndex = -1
  for (let index = messagesList.value.length - 1; index >= 0; index--) {
    if (messagesList.value[index]?.role === 'assistant') {
      lastAssistantIndex = index
      break
    }
  }
  if (lastAssistantIndex < 0) return false
  const content = messagesList.value[lastAssistantIndex]?.content || ''
  const hasLaterUserMessage = messagesList.value
    .slice(lastAssistantIndex + 1)
    .some(item => item?.role === 'user')
  return !hasLaterUserMessage
    && isCreatorAwaitingConfirmation(content)
})

const creatorResultRows = computed(() => {
  const result = creatorResultText.value
  if (!result) return []

  const clean = (value?: string) =>
    value?.replace(/[*`「」“”"]/g, '').replace(/\s+/g, ' ').trim()
  const rows = mergeCreatorResultRows(
    extractCreatorResultRows(result),
    creatorDetailRows.value,
  )

  return rows.length
    ? rows
    : [{ label: '创建结果', value: clean(result) || '智能体已创建完成' }]
})

const createdAgentChatTargetId = computed(() =>
  createdAgentResult.value?.id || creatorResultRows.value.find(row => row.label === '智能体 ID')?.value || '',
)

const creatorProgressStage = computed(() => {
  if (creatorProgress.value < 34) return '正在生成智能体配置'
  if (creatorProgress.value < 68) return '正在装配能力与工具'
  if (creatorProgress.value < 90) return '正在部署并验证'
  return '正在完成最后检查'
})

/** 构建文件前缀字符串 */
function buildFilesPrefix(files: UploadedFileItem[]): string {
  if (!files.length) return ''
  return JSON.stringify({ files }) + '@==##::::##==@'
}

// 构建展示消息
const displayMessages = computed<DisplayMessage[]>(() => {
  const list: DisplayMessage[] = []
  for (let i = 0; i < messagesList.value.length; i++) {
    const m = messagesList.value[i]
    if (!m || m.role === 'system' || !m.content) continue
    if (props.compact && (m.role === 'thinking' || m.role === 'tool')) continue
    if (props.compact && creatorConfirmationIndex.value >= 0 && i > creatorConfirmationIndex.value && m.role === 'assistant') {
      const hasLaterAssistant = messagesList.value
        .slice(i + 1)
        .some(message => message?.role === 'assistant')
      if (isRunning.value || hasLaterAssistant) continue
    }

    let displayId = String(m.id)
    // key 桥接：流式刚结束时，将最后一条 assistant 消息的展示 key 替换为流式 ID
    if (!streamingMessageId.value && lastStreamingKey.value && m.role === 'assistant') {
      const hasLaterAssistant = messagesList.value.slice(i + 1).some(x => x.role === 'assistant')
      if (!hasLaterAssistant) {
        displayId = lastStreamingKey.value
      }
    }

    list.push({
      id: displayId,
      role: m.role as DisplayMessage['role'],
      content: m.content || '',
      createdAt: m.createdAt,
      isStreaming: false,
    })
  }

  if (
    streamingMessageId.value
    && !(props.compact && streamingRole.value === 'thinking')
    && !(props.compact && creatorConfirmationIndex.value >= 0 && isRunning.value)
  ) {
    list.push({
      id: streamingMessageId.value,
      role: streamingRole.value,
      content: streamingContent.value,
      isStreaming: true,
    })
  }  else {
    // 响应加载动画（没有任何推理或文本内容时）
    if (list[list.length - 1]?.role === 'user') {
      list.push({
        id: '',
        role: 'assistant',
        content: '',
        isStreaming: true,
      })
    }
  }
  return list
})

// 重命名模态框
const renameModalVisible = ref(false)
const renameSessionRef = ref<any>(null)
const renameTitle = ref('')
const renameSubmitting = ref(false)

// 新会话
const handleNewSession = async () => {
  // 断开当前 SSE 连接，旧会话继续后台运行
  preserveRunningSession.value = true
  disconnectStream()

  // 开启工作空间的情况特殊处理
  if (hasCodeExecutionConfig.value) {
    if (currentSessionTitle.value === '新对话') {
      preserveRunningSession.value = false
      return
    }

    const existNewSession = otherSessions.value.find((s) => s.title === '新对话')
    if (existNewSession) {
      await selectSession({
        id: existNewSession.id,
        title: existNewSession.title || '新对话',
      } as ChatSessionVO)

      preserveRunningSession.value = false
      return
    }

    const newSession = await createSession(formatSessionTitle(null), true)
    if (!newSession) {
      preserveRunningSession.value = false
      return
    }
    resetSession({
      id: String(newSession.id),
      title:'新对话'
    } as ChatSessionVO)
  } else {
    resetSession(null)
  }

  preserveRunningSession.value = false
}

// 选择会话
const handleSelectSession = async (session: ChatSessionVO) => {
  // 切换前断开当前 SSE，但不中断后台 Agent
  preserveRunningSession.value = true
  disconnectStream()
  resetStreamingState()
  await selectSession(session)
  // 如果目标会话在运行中，触发重连
  if (runningSessions.value.has(String(session.id))) {
    // 注意：不要加 await，否则会阻塞会话切换
    reconnectStream(String(session.id))
  }
  preserveRunningSession.value = false
}

// 会话菜单操作
const handleSessionMenu = async (key: string, session: ChatSessionVO) => {
  const id = String(session.id)
  if (key === 'rename') {
    renameSessionRef.value = session
    renameTitle.value = session.title || '新对话'
    renameModalVisible.value = true
    return
  }
  if (key === 'pin') {
    await pinSession(id)
    if (currentSessionId.value === id) {
      // 若当前会话被置顶，可能需要更新列表，已自动重新加载
    }
    return
  }
  if (key === 'unpin') {
    await unpinSession(id)
    return
  }
  if (key === 'delete') {
    if (isRunning.value) {
      message.info('请等待当前对话完成')
      return
    }
    Modal.confirm({
      title: '确认删除',
      content: '删除后无法恢复，是否继续？',
      onOk: async () => {
        await deleteSession(id)
        if (currentSessionId.value === id) {
          resetSession(null)
        }
      },
    })
    return
  }
}

// 提交重命名
const submitRename = async () => {
  const session = renameSessionRef.value
  if (!session) return
  const title = renameTitle.value.trim() || '新对话'
  renameSubmitting.value = true
  try {
    await updateSessionTitle(session.id, title)
    renameModalVisible.value = false
  } finally {
    renameSubmitting.value = false
  }
}

// 发送工具执行结果
const handelToolContent = async (value: any) => {
  await sendToolContent(value)
}

// 处理交互提交
const handleInteractionSubmit = async (payload: InteractionSubmitPayload) => {
  if (!currentSessionId.value || isRunning.value) return

  const sid = currentSessionId.value
  const data = payload.data as Record<string, unknown>

  // 1. 找到对应的 assistant 消息，将 submittedData 注入 UIP JSON（单次调用完成匹配+注入）
  let updatedContent: string | null = null
  const assistantMsg = [...messagesList.value].reverse().find(m => {
    if (m.role !== 'assistant' || !m.content) return false
    const updated = injectSubmissionToRawContent(m.content, payload.interactionId, data)
    if (updated !== m.content) {
      updatedContent = updated
      return true
    }
    return false
  })
  if (assistantMsg && updatedContent) {
    // 持久化到后端（后端通过 session 的 current_message_id 定位消息，无需传 messageId）
    chatSessionApi.updateCurrentMessageContent(sid, updatedContent)
      .catch(err => console.warn('[UIP] 持久化提交数据失败', err))
    // 同步更新本地消息列表，使渲染器立即读取 submittedData
    assistantMsg.content = updatedContent
  }

  // // 2. 保存用户提交消息到 DB（与 handleSend 保持一致，先 await 再 sendMessage）
  // try {
  //   const res = await chatSessionApi.appendMessage(sid, { role: 'user', content: userText })
  //   if (res.data?.data) messagesList.value.push(res.data.data as ChatMessageVO)
  // } catch (err) {
  //   console.warn('[UIP] 保存用户提交消息失败', err)
  // }

  // 3. 发送给 Agent 继续对话
  const userText = buildUserTextFromPayload(payload)
  await sendMessage(userText, [{ id: 'uip', role: 'user', content: userText }] as ChatMessageVO[])
}

// 处理 UIP 卡片渲染失败重试
const handleUIPRetry = async (uipCode: string) => {
  if (!currentSessionId.value || isRunning.value) return

  // 构造重试消息：提示文本 + 原始 UIP 内容，让智能体参考修正
  const retryText = `上一条消息中的交互卡片生成有误，请重新生成。\n\n原始卡片内容：\n${uipCode}`

  await sendMessage(retryText, [{ id: 'uip', role: 'user', content: retryText }] as ChatMessageVO[])
}

// 发送消息
const handleSend = async () => {
  const text = inputText.value.trim()
  const filesToSend = uploadedFiles.value.filter((f) => !f.uploading)
  const hasFiles = filesToSend.length > 0
  if ((!text && !hasFiles) || !agentId.value || isRunning.value) return

  const finalText = hasFiles ? buildFilesPrefix(filesToSend) + text : text
  const fileIdsToSend = filesToSend.map((f) => f.id)

  // 立即清空输入框和附件，提升交互体验
  inputText.value = ''
  uploadedFiles.value = []

  // 如果没有当前会话，先创建
  if (!currentSessionId.value) {
    const titleInput = text || '新对话'
    const newSession = await createSession(formatSessionTitle(titleInput))
    if (!newSession) return
    currentSessionId.value = String(newSession.id)
    currentSessionTitle.value = newSession.title || '新对话'
  }

  // 保存用户消息
  const userMsg = await chatSessionApi.appendMessage(currentSessionId.value, { role: 'user', content: finalText })
  // 如果是新会话，更新标题
  if (messagesList.value.length <= 1) {
    const title = formatSessionTitle(text || '新对话')
    await updateSessionTitle(currentSessionId.value, title)
    currentSessionTitle.value = title
  }
  messagesList.value.push(userMsg.data.data)

  // 触发流式回复（传入 fileIdsToSend，因输入框已提前清空）
  await sendMessage(
    finalText,
    [{ role: 'user', content: finalText }] as ChatMessageVO[],
    fileIdsToSend
  )
}

// 切换侧边栏（通过 computed setter 自动持久化到 store）
const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

/** 工作空间面板开关状态 */
const workspacePanelOpen = ref(false)

/** 工作空间面板引用（供外部调用 startFileOperation 等） */
const workspacePanelRef = ref<InstanceType<typeof WorkspacePanel> | null>(null)

/**
 * 切换工作空间面板显示/隐藏
 */
const toggleWorkspace = () => {
  workspacePanelOpen.value = !workspacePanelOpen.value
}

// 跟踪所有后台运行中的会话 ID
const runningSessions = ref<Set<string>>(new Set())

/** 临时标志：会话切换/新建期间，阻止 watch(isRunning) 误删旧 session 的运行态 */
const preserveRunningSession = ref(false)
const creatorStep = computed(() => {
  if (creatorCompleted.value || creatorFailed.value || creatorBuildLocked.value || creatorConfirmationIndex.value >= 0) return 3
  if (creatorAwaitingConfirmation.value) return 2
  return 1
})

const showCreatorConfirmationActions = computed(() =>
  creatorAwaitingConfirmation.value && !isRunning.value && !inputText.value.trim(),
)

const formatCreatorHistoryTime = (value?: string) => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (num: number) => String(num).padStart(2, '0')
  return `${date.getMonth() + 1}/${date.getDate()} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

const openCreatorHistory = () => {
  creatorHistoryOpen.value = true
  loadSessions()
}

defineExpose({
  openCreatorHistory,
})

const closeCreatorHistory = () => {
  creatorHistoryOpen.value = false
}

const selectCreatorHistory = async (session: ChatSessionVO) => {
  closeCreatorHistory()
  await handleSelectSession(session)
}

const startNewCreatorHistorySession = async () => {
  closeCreatorHistory()
  await handleNewSession()
}

let creatorDetailRequestSeq = 0

const formatModelCompute = (modelTypes?: unknown[]) => {
  if (!Array.isArray(modelTypes) || !modelTypes.length) return ''
  const labelMap: Record<string, string> = {
    CHAT: '文本推理',
    IMAGE: '图像',
    AUDIO: '音频',
    VIDEO: '视频',
  }
  return modelTypes
    .map(type => labelMap[String(type)] || String(type))
    .join('、')
}

const appendCreatorDetailRows = async () => {
  if (!props.compact || !creatorCompleted.value) {
    creatorDetailRows.value = []
    return
  }
  const seq = ++creatorDetailRequestSeq
  const created = createdAgentResult.value
  creatorDetailRows.value = []
  const parsedRows = extractCreatorResultRows(creatorResultText.value)
  const parsedName = parsedRows.find(row => row.label === '智能体名称')?.value || created?.name || ''
  if (!created?.id && !parsedName) {
    creatorDetailRows.value = []
    return
  }

  try {
    let agent = null as Awaited<ReturnType<typeof agentApi.detail>>['data']['data'] | null
    if (created?.id) {
      const response = await agentApi.detail(created.id)
      agent = response.data?.data || null
    } else if (parsedName) {
      const response = await agentApi.page({ page: 1, size: 10, name: parsedName })
      const records = response.data?.data?.records || []
      agent = records
        .filter(item => item.name === parsedName)
        .sort((a, b) => new Date(b.createdAt || b.updatedAt || 0).getTime() - new Date(a.createdAt || a.updatedAt || 0).getTime())[0] || null
    }
    if (seq !== creatorDetailRequestSeq || !agent) return

    const rows: CreatorResultRow[] = [
      { label: '智能体名称', value: agent.name },
      { label: '智能体 ID', value: String(agent.id) },
    ]
    if (agent.tag) rows.push({ label: '分类', value: agent.tag })

    if (agent.modelConfigId) {
      try {
        const modelResponse = await modelApi.configDetail(String(agent.modelConfigId))
        const model = modelResponse.data?.data
        if (model) {
          rows.push({ label: '模型名称', value: model.name || model.modelId })
          const compute = formatModelCompute(model.modelType)
          if (compute) rows.push({ label: '当前算力', value: compute })
        }
      } catch (error) {
        console.warn('[Creator] 补齐模型信息失败', error)
      }
    }

    try {
      const skillResponse = await agentApi.enabledSkillsOfAgent(String(agent.id))
      const skills = skillResponse.data?.data || []
      rows.push({
        label: '使用技能',
        value: skills.length ? skills.map(skill => skill.name).filter(Boolean).join('、') : '无',
      })
    } catch (error) {
      console.warn('[Creator] 补齐技能信息失败', error)
    }

    if (seq === creatorDetailRequestSeq) {
      creatorDetailRows.value = rows.filter(row => row.value)
    }
  } catch (error) {
    if (seq === creatorDetailRequestSeq) {
      creatorDetailRows.value = []
    }
    console.warn('[Creator] 补齐创建结果详情失败', error)
  }
}

watch([createdAgentResult, creatorResultText, creatorCompleted], () => {
  appendCreatorDetailRows()
}, { immediate: true })

const openCreatedAgentChat = async () => {
  const agentId = createdAgentChatTargetId.value
  if (!agentId) return
  ;(window as any).__PORTAL_V2__ = false
  await router.push({
    name: RouteNames.CHAT,
    params: { agentId },
  })
}

const confirmCreator = async () => {
  if (isRunning.value) return
  creatorBuildLocked.value = true
  creatorProgress.value = Math.max(creatorProgress.value, 8)
  inputText.value = '确认通过，按这个方案创建。'
  await nextTick()
  await handleSend()
}

const continueCreatorRevision = async () => {
  creatorBuildLocked.value = false
  creatorProgress.value = 0
  inputText.value = '我想补充或修改：'
  await nextTick()
  const editor = creatorRootRef.value?.querySelector<HTMLElement>('[contenteditable="true"]')
  editor?.focus()
}

const stopCreatorProgress = () => {
  if (creatorProgressTimer) {
    clearInterval(creatorProgressTimer)
    creatorProgressTimer = null
  }
}

watch([isRunning, creatorStep], ([running, step]) => {
  stopCreatorProgress()
  if (running && step === 3) {
    if (!creatorBuildStartedAt) {
      creatorBuildStartedAt = Date.now()
    }
    creatorProgress.value = Math.max(creatorProgress.value, 8)
    creatorProgressTimer = setInterval(() => {
      const current = creatorProgress.value
      if (current < 38) creatorProgress.value = Math.min(38, current + 4)
      else if (current < 72) creatorProgress.value = Math.min(72, current + 2)
      else if (current < 92) creatorProgress.value = Math.min(92, current + 1)
      if (
        !creatorBuildTimedOut.value
        && isCreatorBuildTimedOut(creatorBuildStartedAt, Date.now(), CREATOR_BUILD_TIMEOUT_MS)
      ) {
        handleCreatorBuildTimeout()
      }
    }, 900)
  } else if (creatorCompleted.value) {
    creatorProgress.value = 100
  } else if (step === 3 && creatorBuildLocked.value) {
    creatorProgress.value = Math.max(creatorProgress.value, 8)
  } else {
    creatorProgress.value = 0
    creatorBuildStartedAt = null
  }
}, { immediate: true })

async function handleCreatorBuildTimeout() {
  if (!props.compact || creatorBuildTimedOut.value || !currentSessionId.value) return
  creatorBuildTimedOut.value = true
  const sid = currentSessionId.value
  const timeoutText = '创建超时：小图自动创建智能体耗时过长，已停止本次创建。请稍后重试，或补充更明确的需求后重新确认。'
  creatorBuildLocked.value = false
  creatorProgress.value = 0
  onCreatorTimeoutMessage(sid, timeoutText)
  disconnectStream()
  resetStreamingState()
  abortRun().catch(() => {
    // stop API failure should not leave the UI locked
  })
}

function onCreatorTimeoutMessage(sid: string, content: string) {
  const localMessage = {
    id: `creator-timeout-${Date.now()}`,
    sessionId: sid,
    role: 'error',
    content,
    parentId: '',
    path: '',
    depth: 0,
    createdAt: '',
  } as ChatMessageVO
  messagesList.value.push(localMessage)
  chatSessionApi.appendMessage(sid, { role: 'error', content })
    .catch(err => console.warn('[Creator] 保存创建超时消息失败', err))
}

watch(currentSessionId, () => {
  creatorBuildLocked.value = false
  creatorBuildTimedOut.value = false
  creatorBuildStartedAt = null
  creatorProgress.value = creatorCompleted.value ? 100 : 0
})

watch(creatorCompleted, (completed) => {
  if (completed) {
    creatorBuildLocked.value = false
    creatorBuildTimedOut.value = false
    creatorBuildStartedAt = null
    creatorProgress.value = 100
  }
})

watch(creatorFailed, (failed) => {
  if (failed) {
    creatorBuildLocked.value = false
    creatorBuildStartedAt = null
    creatorProgress.value = 0
  }
})

// 后台会话状态轮询（5s 间隔）
let pollingTimer: ReturnType<typeof setInterval> | null = null

const settleStoppedRun = async (sid: string) => {
  const next = new Set(runningSessions.value)
  next.delete(sid)
  runningSessions.value = next

  if (sid === currentSessionId.value && isRunning.value) {
    disconnectStream()
    resetStreamingState()
    await loadCurrentMessages()
  }
}

const isCurrentCreatorBuildRunning = () =>
  props.compact && isRunning.value && creatorStep.value === 3

const startPolling = () => {
  if (pollingTimer) return
  pollingTimer = setInterval(async () => {
    if (runningSessions.value.size === 0) {
      stopPolling()
      return
    }
    for (const tid of runningSessions.value) {
      if (!shouldPollCreatorRunStatus(tid, currentSessionId.value, isCurrentCreatorBuildRunning(), creatorStep.value)) continue
      try {
        const running = await getStatus(tid)
        if (!running) {
          await settleStoppedRun(tid)
        }
      } catch {
        // 忽略网络错误
      }
    }
  }, 5000)
}

const stopPolling = () => {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

// 初始化：获取活跃运行列表，若当前会话在运行则重连
onMounted(async () => {
  // v2 门户模式：所有 API 请求使用 tenant=1 上下文
  if (route.query.portal === 'v2') {
    (window as any).__PORTAL_V2__ = true
    portalV2EnabledByThisChat.value = true
  }

  loadSessions()
  try {
    const activeIds = await getActiveRuns()
    runningSessions.value = new Set(activeIds)
    if (currentSessionId.value && activeIds.includes(currentSessionId.value)) {
      reconnectStream(currentSessionId.value)
    }
    if (activeIds.length > 0) {
      startPolling()
    }
  } catch {
    // 忽略初始化错误
  }
})

watch(agentDetail, async (detail) => {
  const prompt = autoInitialPrompt.value
  if (!detail || initialPromptSent.value || !prompt) return
  initialPromptSent.value = true
  inputText.value = prompt
  await nextTick()
  await handleSend()
}, { immediate: true })

// 组件卸载时清理 poll timer
onBeforeUnmount(() => {
  if (portalV2EnabledByThisChat.value) {
    ;(window as any).__PORTAL_V2__ = false
  }
  stopPolling()
  stopCreatorProgress()
  disconnectStream()
})

// 运行状态变化时更新 runningSessions
watch(isRunning, (running) => {
  const sid = currentSessionId.value
  if (!sid) return
  const next = new Set(runningSessions.value)
  if (running) {
    next.add(sid)
    startPolling()
  } else {
    if (!preserveRunningSession.value) {
      next.delete(sid)
    }
  }
  runningSessions.value = next
})
</script>

<template>
  <div ref="creatorRootRef" :class="['chat-page', { 'chat-page-compact': compact }]" style="background-color: #ffffff">
    <div v-if="compact" class="creator-steps">
      <div class="creator-steps-flow">
        <span :class="{ active: creatorStep >= 1 }">1 描述需求</span><i />
        <span :class="{ active: creatorStep >= 2 }">2 确认配置</span><i />
        <span :class="{ active: creatorStep >= 3 }">3 完成创建</span>
      </div>
    </div>
    <ChatSidebar
      v-if="!compact"
      :collapsed="sidebarCollapsed"
      :agent-name="agentDetail?.name"
      :pinned-sessions="pinnedSessions"
      :other-sessions="otherSessions"
      :current-session-id="currentSessionId"
      :running-sessions="runningSessions"
      :user-nickname="userInfo?.nickname"
      :loading="sessionsLoading"
      :has-more="sessionsHasMore"
      :show-account="showAccount"
      @toggle-collapse="toggleSidebar"
      @new-session="handleNewSession"
      @select-session="handleSelectSession"
      @session-menu="handleSessionMenu"
      @load-more="loadMoreSessions"
    />

    <RenameModal
      v-model:visible="renameModalVisible"
      v-model:title="renameTitle"
      :confirm-loading="renameSubmitting"
      @ok="submitRename"
    />

    <div v-if="loadingMessages" class="loading-messages"><LoadingOutlined style="margin-right: 6px" />加载中</div>
    <ChatMain
      v-else
      ref="chatMainRef"
      :title="currentSessionTitle || agentDetail?.name || '对话'"
      :message-size="messagesList.length"
      :welcome-headline="welcomeHeadline || `来和 ${agentDetail?.name || '智能体'} 聊聊吧`"
      :welcome-desc="welcomeDescription || agentDetail?.description || '有什么想说的，直接发给我就好～'"
      :starter-questions="starterQuestions"
      :compact="compact"
      :messages="displayMessages"
      :tool-calls="compact ? [] : toolCallsInProgress"
      :input-value="inputText"
      :uploaded-files="uploadedFiles"
      :isRunning="isRunning"
      :agent-id="agentId"
      :memory-active="memoryActive"
      :plan-active="planActive"
      :enable-memory="enableMemory"
      :enable-planning="compact ? false : enablePlanning"
      :allow-upload-file-type="allowFileType"
      :agent-has-result="agentHasResult"
      :show-tool-process="showToolProcess"
      :tool-process-active="toolProcessActive"
      :workspace-panel-open="workspacePanelOpen"
      :has-code-execution-config="!!hasCodeExecutionConfig"
      :session-id="currentSessionId"
      :session-message-table="currentSessionMessageTable"
      :has-more-history="hasMoreHistory"
      :history-loading="historyLoading"
      :current-plan="compact ? null : currentPlan"
      @update:input-value="inputText = $event"
      @update:uploaded-files="uploadedFiles = $event"
      @memory="handleMemoryChange"
      @plan="handlePlanChange"
      @toolProcess="handelToolProcess"
      @toolContent="handelToolContent"
      @send="handleSend"
      @abort="abortRun"
      @toggle-sidebar="toggleSidebar"
      @toggle-workspace="toggleWorkspace"
      @load-more-history="loadMoreHistory"
      @new-session="handleNewSession"
      @plan-destroyed="currentPlan = null"
      @interaction-submit="handleInteractionSubmit"
      @uip-retry="handleUIPRetry"
    />
    <section
      v-if="compact && creatorHistoryOpen"
      class="creator-history-panel"
      aria-label="历史对话记录"
    >
      <header>
        <div>
          <b>历史对话记录</b>
          <small>查看小图创建过的智能体</small>
        </div>
        <button type="button" aria-label="关闭历史记录" @click="closeCreatorHistory">
          <CloseOutlined />
        </button>
      </header>
      <button type="button" class="creator-history-new" @click="startNewCreatorHistorySession">
        <MessageOutlined />开始新的创建对话
      </button>
      <div class="creator-history-list">
        <button
          v-for="session in creatorHistorySessions"
          :key="session.id"
          type="button"
          :class="['creator-history-item', { active: currentSessionId === String(session.id) }]"
          @click="selectCreatorHistory(session)"
        >
          <span>{{ session.title || '未命名创建对话' }}</span>
          <small>{{ formatCreatorHistoryTime(session.updatedAt || session.createdAt) }}</small>
          <b v-if="currentSessionId === String(session.id)">当前</b>
        </button>
        <div v-if="sessionsLoading" class="creator-history-state">
          <LoadingOutlined />正在加载
        </div>
        <button
          v-else-if="sessionsHasMore"
          type="button"
          class="creator-history-more"
          @click="loadMoreSessions"
        >
          加载更多
        </button>
        <div v-else-if="!creatorHistorySessions.length" class="creator-history-state">
          暂无创建历史
        </div>
      </div>
    </section>
    <div
      v-if="compact && showCreatorConfirmationActions"
      class="creator-confirm-actions"
      aria-label="确认智能体配置"
    >
      <button type="button" class="creator-revise" @click="continueCreatorRevision">
        <EditOutlined />继续补充需求
      </button>
      <button type="button" class="creator-confirm" @click="confirmCreator">
        <CheckCircleOutlined />确认通过<ArrowRightOutlined />
      </button>
    </div>
    <div v-if="compact && isRunning && creatorStep === 3" class="creator-running">
      <CreatorHaloVisual />
      <span>
        <b>正在自动创建智能体</b>
        <small>正在配置能力、部署并验证结果，这期间无需操作</small>
      </span>
      <div class="creator-progress" role="progressbar" :aria-valuenow="creatorProgress" aria-valuemin="0" aria-valuemax="100">
        <div class="creator-progress-meta">
          <span>{{ creatorProgressStage }}</span>
          <strong>{{ creatorProgress }}%</strong>
        </div>
        <div class="creator-progress-track">
          <i :style="{ width: `${creatorProgress}%` }" />
        </div>
      </div>
    </div>
    <section v-if="compact && creatorCompleted && !isRunning" class="creator-success">
      <div class="creator-success-mark"><CheckCircleOutlined /></div>
      <span class="creator-success-eyebrow">创建完成</span>
      <h2>你的智能体已经准备好了</h2>
      <p>小图已完成配置、部署和验证，相关信息如下。</p>
      <div class="creator-result-card">
        <header>
          <div><b>创建结果</b><small>已完成部署与启用</small></div>
          <span><CheckCircleOutlined />创建成功</span>
        </header>
        <dl class="creator-result-table">
          <template v-for="row in creatorResultRows" :key="row.label">
            <dt>{{ row.label }}</dt>
            <dd :class="{ 'is-id': row.label === '智能体 ID' }">{{ row.value }}</dd>
          </template>
        </dl>
      </div>
      <button
        v-if="createdAgentChatTargetId"
        type="button"
        class="creator-chat-created"
        @click="openCreatedAgentChat"
      >
        <MessageOutlined />立即跟新建立的智能体对话<ArrowRightOutlined />
      </button>
    </section>
    <!-- 工作空间面板（作为 flex 子项从右侧滑出） -->
    <WorkspacePanel
      v-if="currentSessionId && !!hasCodeExecutionConfig && !loadingMessages"
      ref="workspacePanelRef"
      :session-id="currentSessionId"
      :class="{ open: workspacePanelOpen }"
      @close="workspacePanelOpen = false"
    />
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/chat/index.scss' as *;
.chat-page-compact {
  height: 100%;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
  position: relative;
}
.chat-page-compact > .chat-main {
  flex: 1;
  min-height: 0;
  height: auto;
}
.creator-steps {
  height: 64px;
  flex: 0 0 auto;
  padding: 0 24px;
  display: flex;
  align-items: center;
  gap: 12px;
  color: #a0a5b1;
  font-size: 13px;
  border-bottom: 1px solid #eef0f4;
}
.creator-steps-flow {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
}
.creator-steps span {
  white-space: nowrap;
}
.creator-steps span.active {
  color: #2f73d9;
  font-weight: 700;
}
.creator-steps i {
  height: 1px;
  flex: 1;
  margin: 0 9px;
  background: #e1e4eb;
}
.creator-history-panel {
  position: absolute;
  inset: 64px 0 0;
  z-index: 11;
  display: flex;
  flex-direction: column;
  color: #26354c;
  background: #fff;
}
.creator-history-panel > header {
  min-height: 70px;
  padding: 16px 24px 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #eef0f4;
}
.creator-history-panel > header div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.creator-history-panel > header b {
  color: #18233a;
  font-size: 16px;
}
.creator-history-panel > header small {
  color: #7c889a;
  font-size: 12px;
}
.creator-history-panel > header button {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  color: #7b8493;
  border: 0;
  border-radius: 9px;
  background: transparent;
  cursor: pointer;
}
.creator-history-panel > header button:hover {
  color: #26354c;
  background: #f3f6fa;
}
.creator-history-new {
  height: 42px;
  margin: 14px 20px 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  color: #2f73d9;
  border: 1px solid #cfe0f8;
  border-radius: 11px;
  background: #f8fbff;
  font-weight: 600;
  cursor: pointer;
}
.creator-history-list {
  flex: 1;
  min-height: 0;
  padding: 0 16px 20px;
  overflow-y: auto;
}
.creator-history-item {
  width: 100%;
  min-height: 58px;
  margin-top: 8px;
  padding: 11px 12px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 4px 10px;
  text-align: left;
  color: #26354c;
  border: 1px solid #e5ebf3;
  border-radius: 12px;
  background: #fff;
  cursor: pointer;
}
.creator-history-item:hover,
.creator-history-item.active {
  border-color: #bcd3f4;
  background: #f7fbff;
}
.creator-history-item span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 600;
}
.creator-history-item small {
  grid-column: 1;
  color: #8995a7;
  font-size: 12px;
}
.creator-history-item b {
  grid-row: 1 / span 2;
  grid-column: 2;
  align-self: center;
  color: #2f73d9;
  font-size: 12px;
}
.creator-history-state,
.creator-history-more {
  width: 100%;
  margin-top: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #8a96a8;
  font-size: 12px;
}
.creator-history-more {
  height: 36px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
  cursor: pointer;
}
.creator-confirm-actions {
  position: absolute;
  z-index: 7;
  right: 18px;
  bottom: 112px;
  left: 18px;
  display: grid;
  grid-template-columns: 1fr 1.12fr;
  gap: 10px;
  padding: 12px;
  border: 1px solid #dfe7f3;
  border-radius: 16px;
  background: #fffffff2;
  box-shadow: 0 14px 38px #1f3d681a;
  backdrop-filter: blur(12px);
}
.creator-confirm-actions button {
  height: 42px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}
.creator-revise {
  color: #526174;
  border: 1px solid #dce3ec;
  background: #f8fafc;
}
.creator-confirm {
  color: #fff;
  border: 1px solid #2f73d9;
  background: #2f73d9;
  box-shadow: 0 8px 20px #2f73d933;
}
.creator-confirm:hover {
  background: #2467c7;
}
.creator-running {
  position: absolute;
  inset: 64px 0 0;
  z-index: 8;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 18px;
  color: #4772a9;
  text-align: center;
  background:
    radial-gradient(circle at 50% 38%, #e5f6f2 0, #f1f9fb 36%, #fffffff2 72%);
  backdrop-filter: blur(8px);
}
.creator-running span {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.creator-running b {
  color: #244f86;
  font-size: 16px;
  font-weight: 650;
}
.creator-running small {
  color: #7890ad;
  font-size: 12px;
}
.creator-progress {
  width: min(320px, calc(100% - 48px));
  padding: 14px 16px;
  border: 1px solid #dce8f7;
  border-radius: 14px;
  background: #ffffffd9;
  box-shadow: 0 12px 32px #31517d12;
}
.creator-progress-meta {
  margin-bottom: 9px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #657992;
  font-size: 12px;
}
.creator-progress-meta strong {
  color: #2f73d9;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.creator-progress-track {
  height: 7px;
  overflow: hidden;
  border-radius: 999px;
  background: #e8eef7;
}
.creator-progress-track i {
  height: 100%;
  display: block;
  border-radius: inherit;
  background: linear-gradient(90deg, #78ade8, #2f73d9);
  box-shadow: 0 0 12px #2f73d966;
  transition: width .72s cubic-bezier(.22, 1, .36, 1);
}
.creator-success {
  position: absolute;
  inset: 64px 0 0;
  z-index: 9;
  padding: 52px 26px 32px;
  overflow-y: auto;
  color: #243147;
  text-align: center;
  background:
    radial-gradient(circle at 50% 14%, #e3f2ff 0, #f7fbff 28%, #fff 62%);
}
.creator-success-mark {
  width: 58px;
  height: 58px;
  margin: 0 auto 16px;
  display: grid;
  place-items: center;
  color: #fff;
  font-size: 28px;
  border-radius: 18px;
  background: #2f73d9;
  box-shadow: 0 14px 32px #2f73d93d;
}
.creator-success-eyebrow {
  color: #2f73d9;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: .12em;
}
.creator-success h2 {
  margin: 8px 0 7px;
  color: #17233b;
  font-size: 22px;
}
.creator-success > p {
  margin: 0 auto 24px;
  color: #748196;
  font-size: 13px;
}
.creator-result-card {
  overflow: hidden;
  border: 1px solid #dce5f1;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 16px 40px #31517d14;
}
.creator-result-card > header {
  min-height: 64px;
  padding: 14px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  text-align: left;
  border-bottom: 1px solid #e7edf5;
  background: #f8fbff;
}
.creator-result-card > header > div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.creator-result-card > header b {
  color: #1d2b42;
  font-size: 15px;
}
.creator-result-card > header small {
  color: #8491a4;
  font-size: 11px;
}
.creator-result-card > header > span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #2f73d9;
  font-size: 12px;
  font-weight: 600;
}
.creator-result-table {
  margin: 0;
  display: grid;
  grid-template-columns: 104px minmax(0, 1fr);
  text-align: left;
}
.creator-result-table dt,
.creator-result-table dd {
  margin: 0;
  padding: 13px 14px;
  border-bottom: 1px solid #e9eef5;
  line-height: 1.6;
}
.creator-result-table dt {
  color: #68778c;
  font-size: 12px;
  font-weight: 600;
  background: #f7faff;
}
.creator-result-table dd {
  min-width: 0;
  color: #26354c;
  font-size: 13px;
  overflow-wrap: anywhere;
}
.creator-result-table dd.is-id {
  color: #255faa;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 12px;
  font-weight: 600;
}
.creator-result-table dt:nth-last-of-type(1),
.creator-result-table dd:nth-last-of-type(1) {
  border-bottom: 0;
}
.creator-chat-created {
  width: 100%;
  min-height: 46px;
  margin-top: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  border: 1px solid #2f73d9;
  border-radius: 12px;
  background: #2f73d9;
  box-shadow: 0 12px 26px #2f73d92e;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}
.creator-chat-created:hover {
  background: #2467c7;
}
.loading-messages {
  width: 100%;
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
}
</style>
