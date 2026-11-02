<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { message } from 'ant-design-vue'
import type { FormInstance, Rule } from 'ant-design-vue/es/form'
import { md5 } from 'js-md5'
import {
  CalendarOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  CopyOutlined,
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  KeyOutlined,
  LoginOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  TeamOutlined,
  UploadOutlined,
} from '@ant-design/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { useAccountStore } from '@/stores'
import { isPlatformAdminUsername } from '@/utils/platformAdmin'
import {
  batchRemoveExamCandidateAccounts,
  batchResetExamCandidatePasswords,
  batchUpdateExamCandidateAccountsEnabled,
  changeExamCandidatePassword,
  createExamCandidateAccount,
  downloadCandidateImportTemplate,
  getExamCandidateStatus,
  importExamCandidates,
  listExamCampaignsForAdmin,
  removeExamCandidateAccount,
  resetExamCandidatePassword,
  updateExamCandidateAccount,
  updateExamCandidateAccountEnabled,
  type ExamCandidateImportOutcome,
  type ExamCandidateImportResult,
  type ExamCandidateStatus,
  type ExamCampaign,
  type ExamCandidateStatusSnapshot,
} from '@/api/exam'
import { RouteNames } from '@/router'

const router = useRouter()
const route = useRoute()
const accountStore = useAccountStore()
const campaigns = ref<ExamCampaign[]>([])
const selectedCampaignId = ref<string | number>()
const snapshot = ref<ExamCandidateStatusSnapshot | null>(null)
const loading = ref(false)
const campaignsLoading = ref(false)
const nowMs = ref(Date.now())
const clockOffsetMs = ref(0)
const lastUpdated = ref('')
const searchKeyword = ref('')
const accountModalVisible = ref(false)
const accountSaving = ref(false)
const accountFormRef = ref<FormInstance>()
const editingCandidate = ref<ExamCandidateStatus | null>(null)
const passwordModalVisible = ref(false)
const passwordSaving = ref(false)
const passwordFormRef = ref<FormInstance>()
const passwordCandidate = ref<ExamCandidateStatus | null>(null)
const selectedRowKeys = ref<Array<string | number>>([])
const batchLoading = ref(false)
const pagination = ref({ current: 1, pageSize: 10, total: 0, showSizeChanger: true })
const credentialModalVisible = ref(false)
const credential = reactive({
  title: '账号信息',
  nickname: '',
  username: '',
  password: '',
  tip: '',
})
const importModalVisible = ref(false)
const importFile = ref<File | null>(null)
const importing = ref(false)
const importResult = ref<ExamCandidateImportResult | null>(null)
const importOutcomeColumns = [
  { title: 'Excel 行号', dataIndex: 'rowNumber', key: 'rowNumber', width: 90 },
  { title: '姓名', dataIndex: 'name', key: 'name', width: 110 },
  { title: '账号', dataIndex: 'username', key: 'username', width: 140 },
  { title: '结果', key: 'outcome' },
]

const accountForm = reactive({
  nickname: '',
  username: '',
  email: '',
  password: '12345678',
  organization: '',
})
const passwordForm = reactive({ password: '' })

let pollTimer: number | undefined
let tickTimer: number | undefined

const isPlatformAdmin = computed(
  () => isPlatformAdminUsername(accountStore.userInfo?.username),
)
const rows = computed(() => {
  const candidates = snapshot.value?.candidates || []
  const keyword = searchKeyword.value.trim().toLowerCase()
  if (!keyword) return candidates
  return candidates.filter((candidate) => [
    candidate.candidateName,
    candidate.username,
    candidate.email,
    candidate.organization,
  ].some((value) => value?.toLowerCase().includes(keyword)))
})
const pagedRows = computed(() => {
  const { current, pageSize } = pagination.value
  const start = (current - 1) * pageSize
  return rows.value.slice(start, start + pageSize)
})
const selectedEnrollmentIds = computed(() =>
  selectedRowKeys.value.map((value) => Number(value)).filter((value) => Number.isFinite(value)),
)
const rowSelection = computed(() => ({
  selectedRowKeys: selectedRowKeys.value,
  onChange: (keys: Array<string | number>) => {
    selectedRowKeys.value = keys
  },
}))
const serverNowMs = computed(() => nowMs.value + clockOffsetMs.value)
const isEditingCandidate = computed(() => Boolean(editingCandidate.value?.enrollmentId))
const accountModalTitle = computed(() => isEditingCandidate.value ? '编辑考生账号' : '新增考生账号')
const accountRules = computed<Record<string, Rule[]>>(() => ({
  nickname: [
    { required: true, message: '请输入考生姓名', trigger: 'blur' },
    { max: 10, message: '考生姓名不能超过10个字符', trigger: 'blur' },
  ],
  username: [
    { required: true, message: '请输入考生账号', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9._-]{4,20}$/, message: '请输入4～20位字母、数字、点、下划线或短横线', trigger: 'blur' },
  ],
  email: [{ type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }],
  password: isEditingCandidate.value ? [] : [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为6～20位', trigger: 'blur' },
  ],
}))
const passwordRules: Record<string, Rule[]> = {
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为6～20位', trigger: 'blur' },
  ],
}

function parseTime(value?: string): number | null {
  if (!value) return null
  const normalized = value.includes(' ') && !value.includes('T') ? value.replace(' ', 'T') : value
  const parsed = dayjs(normalized).valueOf()
  return Number.isNaN(parsed) ? null : parsed
}

function formatTime(value?: string): string {
  const timestamp = parseTime(value)
  return timestamp === null ? '未设置' : dayjs(timestamp).format('YYYY-MM-DD HH:mm:ss')
}

function formatDuration(seconds?: number | null): string {
  if (seconds === null || seconds === undefined) return '—'
  const value = Math.max(0, Math.floor(seconds))
  const hours = Math.floor(value / 3600)
  const minutes = Math.floor((value % 3600) / 60)
  const rest = value % 60
  return [hours, minutes, rest].map((item) => String(item).padStart(2, '0')).join(':')
}

function stateLabel(state?: string): string {
  return ({ WAITING: '待开考', OPEN: '考试中', ENDED: '已结束' }[state || ''] || '未设置')
}

function stateColor(state?: string): string {
  return ({ WAITING: 'blue', OPEN: 'green', ENDED: 'default' }[state || ''] || 'default')
}

function submissionLabel(status?: string): string {
  return ({
    DRAFT: '未交卷',
    AI_QUEUED: '已交卷 · AI排队',
    REVIEW_PENDING: '待评审',
    PUBLISHED: '已发布',
    TIMEOUT_SUBMITTED: '超时自动交卷',
    PARTIAL_TIMEOUT_SUBMITTED: '超时交卷（内容不完整）',
    NO_ANSWER: '超时交卷（未作答）',
  }[status || ''] || status || '未交卷')
}

function submissionColor(status?: string): string {
  return ({
    DRAFT: 'processing',
    AI_QUEUED: 'blue',
    REVIEW_PENDING: 'gold',
    PUBLISHED: 'green',
    TIMEOUT_SUBMITTED: 'orange',
    PARTIAL_TIMEOUT_SUBMITTED: 'volcano',
    NO_ANSWER: 'red',
  }[status || ''] || 'default')
}

function liveElapsed(row: ExamCandidateStatus): number {
  if (!row.entered || snapshot.value?.examState === 'WAITING') return 0
  const start = parseTime(snapshot.value?.examStartAt)
  const end = parseTime(snapshot.value?.examEndAt)
  if (start === null) return 0
  const target = snapshot.value?.examState === 'ENDED' && end !== null ? end : serverNowMs.value
  return Math.max(0, Math.floor((target - start) / 1000))
}

function liveRemaining(): number {
  const state = snapshot.value?.examState
  const target = state === 'WAITING'
    ? parseTime(snapshot.value?.examStartAt)
    : parseTime(snapshot.value?.examEndAt)
  if (target === null || target === undefined || state === 'ENDED') return 0
  return Math.max(0, Math.ceil((target - serverNowMs.value) / 1000))
}

function elapsedText(row: ExamCandidateStatus): string {
  if (!row.entered) return '未进入'
  if (snapshot.value?.examState === 'WAITING') return '待开考'
  return formatDuration(liveElapsed(row))
}

function remainingText(): string {
  if (snapshot.value?.examState === 'WAITING') return `距开考 ${formatDuration(liveRemaining())}`
  if (snapshot.value?.examState === 'ENDED') return '已结束'
  return formatDuration(liveRemaining())
}

async function loadCampaigns() {
  if (!isPlatformAdmin.value) {
    await router.replace({ name: RouteNames.EXPLORE })
    return
  }
  campaignsLoading.value = true
  try {
    const response = await listExamCampaignsForAdmin()
    campaigns.value = response.data.data || []
    const requestedCampaignId = route.query.campaignId
    const requestedCampaign = campaigns.value.find(
      (item) => String(item.id) === String(requestedCampaignId || ''),
    )
    const selectedExists = campaigns.value.some((item) => String(item.id) === String(selectedCampaignId.value))
    if (requestedCampaign) selectedCampaignId.value = requestedCampaign.id
    else if (!selectedExists) selectedCampaignId.value = campaigns.value[0]?.id as string | undefined
    await loadStatus()
  } catch {
    message.warning('加载考试批次失败')
  } finally {
    campaignsLoading.value = false
  }
}

async function loadStatus() {
  if (!selectedCampaignId.value || !isPlatformAdmin.value) return
  loading.value = true
  try {
    const response = await getExamCandidateStatus(selectedCampaignId.value)
    snapshot.value = response.data.data || null
    const serverTime = parseTime(snapshot.value?.serverNow)
    if (serverTime !== null) clockOffsetMs.value = serverTime - Date.now()
    lastUpdated.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  } catch {
    // 5s 轮询失败不弹红框，等下一轮自动恢复
  } finally {
    loading.value = false
  }
}

function handleCampaignChange() {
  void loadStatus()
}

function goSchedule() {
  // 携带当前批次回跳考试场次页，保持跨页面上下文
  void router.push({
    name: RouteNames.EXAM_ADMIN,
    query: selectedCampaignId.value ? { campaignId: String(selectedCampaignId.value) } : {},
  })
}

function resetAccountForm() {
  accountForm.nickname = ''
  accountForm.username = ''
  accountForm.email = ''
  accountForm.password = '12345678'
  accountForm.organization = ''
}

function openCreateCandidate() {
  if (!selectedCampaignId.value) {
    message.warning('请先选择考试批次')
    return
  }
  editingCandidate.value = null
  resetAccountForm()
  accountModalVisible.value = true
}

function openEditCandidate(candidate: ExamCandidateStatus) {
  editingCandidate.value = candidate
  accountForm.nickname = candidate.candidateName || ''
  accountForm.username = candidate.username || ''
  accountForm.email = candidate.email || ''
  accountForm.password = ''
  accountForm.organization = candidate.organization || ''
  accountModalVisible.value = true
}

async function saveCandidateAccount() {
  if (!selectedCampaignId.value) return
  try {
    await accountFormRef.value?.validate()
    accountSaving.value = true
    const payload = {
      nickname: accountForm.nickname.trim(),
      username: accountForm.username.trim(),
      email: accountForm.email.trim() || undefined,
      organization: accountForm.organization.trim() || undefined,
    }
    if (editingCandidate.value?.enrollmentId) {
      await updateExamCandidateAccount(
        selectedCampaignId.value,
        editingCandidate.value.enrollmentId,
        payload,
      )
      message.success('考生账号已更新')
      accountModalVisible.value = false
    } else {
      const plainPassword = accountForm.password
      await createExamCandidateAccount(selectedCampaignId.value, {
        ...payload,
        password: md5(plainPassword),
      })
      accountModalVisible.value = false
      openCredential({
        title: '考生账号创建成功',
        nickname: payload.nickname,
        username: payload.username,
        password: plainPassword,
        tip: '请将账号信息发送给考生。该密码仅在此处显示一次。',
      })
    }
    await loadStatus()
  } catch (error: unknown) {
    if (error && typeof error === 'object' && 'errorFields' in error) return
  } finally {
    accountSaving.value = false
  }
}

function openChangePassword(candidate: ExamCandidateStatus) {
  passwordCandidate.value = candidate
  passwordForm.password = ''
  passwordModalVisible.value = true
}

async function saveCandidatePassword() {
  if (!selectedCampaignId.value || !passwordCandidate.value?.enrollmentId) return
  try {
    await passwordFormRef.value?.validate()
    passwordSaving.value = true
    const plainPassword = passwordForm.password
    await changeExamCandidatePassword(
      selectedCampaignId.value,
      passwordCandidate.value.enrollmentId,
      md5(plainPassword),
    )
    passwordModalVisible.value = false
    openCredential({
      title: '密码修改成功',
      nickname: passwordCandidate.value.candidateName || '',
      username: passwordCandidate.value.username || '',
      password: plainPassword,
      tip: '请将新密码发送给考生。该密码仅在此处显示一次。',
    })
  } catch (error: unknown) {
    if (error && typeof error === 'object' && 'errorFields' in error) return
  } finally {
    passwordSaving.value = false
  }
}

async function resetCandidatePassword(candidate: ExamCandidateStatus) {
  if (!selectedCampaignId.value || !candidate.enrollmentId) return
  try {
    await resetExamCandidatePassword(selectedCampaignId.value, candidate.enrollmentId)
    openCredential({
      title: '密码已重置',
      nickname: candidate.candidateName || '',
      username: candidate.username || '',
      password: '12345678',
      tip: '重置后统一为默认密码 12345678，请通知考生尽快修改。',
    })
  } catch {
    // 统一请求层会展示服务端错误信息。
  }
}

async function toggleCandidateEnabled(candidate: ExamCandidateStatus) {
  if (!selectedCampaignId.value || !candidate.enrollmentId) return
  const enabled = !candidate.accountEnabled
  try {
    await updateExamCandidateAccountEnabled(selectedCampaignId.value, candidate.enrollmentId, enabled)
    message.success(enabled ? '账号已启用' : '账号已停用')
    await loadStatus()
  } catch {
    // 统一请求层会展示服务端错误信息。
  }
}

async function removeCandidate(candidate: ExamCandidateStatus) {
  if (!selectedCampaignId.value || !candidate.enrollmentId) return
  try {
    await removeExamCandidateAccount(selectedCampaignId.value, candidate.enrollmentId)
    message.success('考生已移出本场考试，历史答题与成绩仍保留')
    selectedRowKeys.value = selectedRowKeys.value.filter(
      (key) => String(key) !== String(candidate.enrollmentId),
    )
    await loadStatus()
  } catch {
    // 统一请求层会展示服务端错误信息。
  }
}

function openCredential(payload: {
  title: string
  nickname: string
  username: string
  password: string
  tip: string
}) {
  credential.title = payload.title
  credential.nickname = payload.nickname
  credential.username = payload.username
  credential.password = payload.password
  credential.tip = payload.tip
  credentialModalVisible.value = true
}

async function copyCredential() {
  const text = `姓名：${credential.nickname}\n账号：${credential.username}\n密码：${credential.password}`
  try {
    await navigator.clipboard.writeText(text)
    message.success('账号信息已复制')
  } catch {
    message.warning('复制失败，请手动选中复制')
  }
}

function ensureSelected(): Array<string | number> {
  if (!selectedRowKeys.value.length) {
    message.warning('请先勾选考生')
    return []
  }
  return selectedRowKeys.value
}

async function batchResetPasswords() {
  if (!selectedCampaignId.value) return
  const ids = ensureSelected()
  if (!ids.length) return
  batchLoading.value = true
  try {
    const result = await batchResetExamCandidatePasswords(selectedCampaignId.value, ids)
    const count = result?.data ?? ids.length
    openCredential({
      title: `已重置 ${count} 个账号密码`,
      nickname: `已选 ${ids.length} 名考生`,
      username: ids.map((id) => rows.value.find((row) => String(row.enrollmentId) === String(id))?.username || id).join('、'),
      password: '12345678',
      tip: '所选考生密码已统一重置为 12345678，请批量通知。',
    })
    await loadStatus()
  } catch {
    // 统一请求层会展示服务端错误信息。
  } finally {
    batchLoading.value = false
  }
}

async function batchToggleEnabled(enabled: boolean) {
  if (!selectedCampaignId.value) return
  const ids = ensureSelected()
  if (!ids.length) return
  batchLoading.value = true
  try {
    await batchUpdateExamCandidateAccountsEnabled(selectedCampaignId.value, ids, enabled)
    message.success(enabled ? `已启用 ${ids.length} 个账号` : `已停用 ${ids.length} 个账号`)
    await loadStatus()
  } catch {
    // 统一请求层会展示服务端错误信息。
  } finally {
    batchLoading.value = false
  }
}

async function batchRemoveCandidates() {
  if (!selectedCampaignId.value) return
  const ids = ensureSelected()
  if (!ids.length) return
  batchLoading.value = true
  try {
    await batchRemoveExamCandidateAccounts(selectedCampaignId.value, ids)
    message.success(`已移出 ${ids.length} 名考生，历史答题与成绩仍保留`)
    selectedRowKeys.value = []
    await loadStatus()
  } catch {
    // 统一请求层会展示服务端错误信息。
  } finally {
    batchLoading.value = false
  }
}

function handleTableChange(page: { current?: number; pageSize?: number }) {
  pagination.value.current = page.current || 1
  pagination.value.pageSize = page.pageSize || pagination.value.pageSize
}

watch(searchKeyword, () => {
  pagination.value.current = 1
})

watch(selectedCampaignId, () => {
  selectedRowKeys.value = []
  pagination.value.current = 1
})

function exportCandidateAccounts() {
  if (!rows.value.length) {
    message.warning('当前没有可导出的考生')
    return
  }
  const campaignName = campaigns.value.find((item) => String(item.id) === String(selectedCampaignId.value))?.name || '考试'
  const entryUrl = `${window.location.origin}${import.meta.env.BASE_URL}#/exam/portal`
  const examWindow = snapshot.value?.examStartAt && snapshot.value?.examEndAt
    ? `${fmtDateTime(snapshot.value.examStartAt)} ~ ${fmtDateTime(snapshot.value.examEndAt)}`
    : '以考试通知为准'
  const header = ['姓名', '登录账号', '邮箱', '组织/班级', '账号状态', '备注']
  const body = rows.value.map((row) => [
    row.candidateName || '',
    row.username || '',
    row.email || '',
    row.organization || '',
    row.accountEnabled ? '正常' : '已停用',
    '密码在创建或重置时一次性展示，请通过安全渠道单独下发，勿在本表记录明文密码',
  ])
  const info = [
    ['考试名称', campaignName],
    ['考试时间', examWindow],
    ['考试入口', entryUrl],
    ['登录方式', '使用考生账号密码在考试入口登录，登录后自动进入考试门户'],
  ]
  const csv = [info, [], header, ...body]
    .map((line) => line.map((cell) => `"${String(cell).replace(/"/g, '""')}"`).join(','))
    .join('\r\n')
  const blob = new Blob([`﻿${csv}`], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${campaignName}-考生账号表.csv`
  link.click()
  URL.revokeObjectURL(url)
  message.success('账号表已导出（不含密码，请单独下发）')
}

function fmtDateTime(value?: string | null) {
  if (!value) return '待定'
  return dayjs(value).format('YYYY-MM-DD HH:mm')
}

function saveBlobFile(blob: Blob, filename: string) {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => window.URL.revokeObjectURL(url), 0)
}

function openImportModal() {
  importFile.value = null
  importResult.value = null
  importModalVisible.value = true
}

/** a-upload beforeUpload：拦截自动上传，仅记录文件 */
function beforeImportUpload(file: File) {
  if (!/\.(xlsx|xls)$/i.test(file.name)) {
    message.warning('仅支持 .xlsx / .xls 格式的 Excel 文件')
    return false
  }
  if (file.size > 2 * 1024 * 1024) {
    message.warning('Excel 文件不能超过 2MB')
    return false
  }
  importFile.value = file
  return false
}

async function downloadTemplate() {
  if (!selectedCampaignId.value) return
  try {
    const res = await downloadCandidateImportTemplate(selectedCampaignId.value)
    saveBlobFile(res.data as unknown as Blob, '考生批量导入模板.xlsx')
  } catch {
    message.warning('模板下载失败，请稍后重试')
  }
}

async function submitImport() {
  if (!selectedCampaignId.value) return
  if (!importFile.value) {
    message.warning('请先选择考生名单 Excel 文件')
    return
  }
  importing.value = true
  importResult.value = null
  try {
    const res = await importExamCandidates(selectedCampaignId.value, importFile.value)
    const result = res.data.data || null
    if (!result) throw new Error('empty')
    importResult.value = result
    if (result.successCount > 0) {
      message.success(`导入完成：成功 ${result.successCount} 名，失败 ${result.failureCount} 名`)
      await loadStatus()
    } else {
      message.warning('没有考生导入成功，请查看失败原因后修正名单')
    }
  } catch {
    message.warning('导入失败，请稍后重试')
  } finally {
    importing.value = false
  }
}

onMounted(async () => {
  await loadCampaigns()
  tickTimer = window.setInterval(() => {
    nowMs.value = Date.now()
  }, 1000)
  pollTimer = window.setInterval(() => void loadStatus(), 5000)
})

onBeforeUnmount(() => {
  if (tickTimer) window.clearInterval(tickTimer)
  if (pollTimer) window.clearInterval(pollTimer)
})
</script>

<template>
  <main class="candidate-status-page">
    <header class="page-heading">
      <div class="title-mark"><TeamOutlined /></div>
      <div class="heading-copy">
        <span>考试管理</span>
        <h1>考生账号与状态管理</h1>
        <p>创建和维护考生账号，并实时查看登录、入场和提交进度。</p>
      </div>
      <div class="heading-actions">
        <a-button type="primary" :disabled="!selectedCampaignId" @click="openImportModal">
          <template #icon><UploadOutlined /></template>
          批量导入
        </a-button>
        <a-button :disabled="!selectedCampaignId" @click="openCreateCandidate">
          <template #icon><PlusOutlined /></template>
          新增考生
        </a-button>
        <a-button :disabled="!selectedCampaignId || !rows.length" @click="exportCandidateAccounts">
          <template #icon><DownloadOutlined /></template>
          导出账号表
        </a-button>
        <a-button @click="goSchedule">
          <template #icon><CalendarOutlined /></template>
          时间设置
        </a-button>
        <a-button :loading="loading || campaignsLoading" @click="loadCampaigns">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
      </div>
    </header>

    <section class="toolbar-card">
      <div class="toolbar-label">选择考试批次</div>
      <a-select
        v-model:value="selectedCampaignId"
        :loading="campaignsLoading"
        style="width: min(460px, 100%)"
        placeholder="请选择考试批次"
        @change="handleCampaignChange"
      >
        <a-select-option v-for="item in campaigns" :key="item.id" :value="item.id">
          {{ item.name }}
        </a-select-option>
      </a-select>
      <div v-if="snapshot" class="window-summary">
        <a-tag :color="stateColor(snapshot.examState)">{{ stateLabel(snapshot.examState) }}</a-tag>
        <span>{{ formatTime(snapshot.examStartAt) }} — {{ formatTime(snapshot.examEndAt) }}</span>
        <span v-if="lastUpdated" class="muted">更新于 {{ lastUpdated }} · 每5秒自动刷新</span>
      </div>
    </section>

    <a-alert
      v-if="snapshot && !snapshot.loginStatusAvailable"
      type="warning"
      show-icon
      message="当前无法读取登录会话"
      description="考试进入和时间数据仍可查看，登录状态将在缓存服务恢复后自动更新。"
      class="status-alert"
    />

    <section v-if="snapshot" class="stats-grid">
      <a-card :bordered="false" class="stat-card">
        <div class="stat-icon total"><TeamOutlined /></div>
        <div><span>考生账号</span><strong>{{ snapshot.totalCandidates ?? 0 }}</strong></div>
      </a-card>
      <a-card :bordered="false" class="stat-card">
        <div class="stat-icon online"><LoginOutlined /></div>
        <div><span>当前已登录</span><strong>{{ snapshot.loginStatusAvailable ? snapshot.loggedInCount : '—' }}</strong></div>
      </a-card>
      <a-card :bordered="false" class="stat-card">
        <div class="stat-icon entered"><CheckCircleOutlined /></div>
        <div><span>已进入考试</span><strong>{{ snapshot.enteredCount ?? 0 }}</strong></div>
      </a-card>
      <a-card :bordered="false" class="stat-card">
        <div class="stat-icon submitted"><ClockCircleOutlined /></div>
        <div><span>已交卷</span><strong>{{ snapshot.submittedCount ?? 0 }}</strong></div>
      </a-card>
    </section>

    <a-card v-if="snapshot" :bordered="false" class="candidate-card">
      <template #title>考生账号</template>
      <template #extra>
        <a-input
          v-model:value="searchKeyword"
          allow-clear
          placeholder="搜索姓名、账号、邮箱或组织"
          class="candidate-search"
        >
          <template #prefix><SearchOutlined /></template>
        </a-input>
      </template>
      <div class="batch-bar">
        <span class="batch-count">已选 {{ selectedRowKeys.length }} 人</span>
        <a-button size="small" :disabled="!selectedRowKeys.length" :loading="batchLoading" @click="batchResetPasswords">
          批量重置密码
        </a-button>
        <a-button size="small" :disabled="!selectedRowKeys.length" :loading="batchLoading" @click="batchToggleEnabled(true)">
          批量启用
        </a-button>
        <a-button size="small" :disabled="!selectedRowKeys.length" :loading="batchLoading" @click="batchToggleEnabled(false)">
          批量停用
        </a-button>
        <a-popconfirm
          title="确认将所选考生移出本场考试吗？历史答题与成绩会保留。"
          ok-text="移出考试"
          cancel-text="取消"
          @confirm="batchRemoveCandidates"
        >
          <a-button size="small" danger :disabled="!selectedRowKeys.length" :loading="batchLoading">
            批量移出
          </a-button>
        </a-popconfirm>
      </div>
      <a-table
        :data-source="pagedRows"
        :loading="loading"
        :pagination="{
          ...pagination,
          total: rows.length,
          showTotal: (total: number) => `共 ${total} 人`,
        }"
        :row-selection="rowSelection"
        row-key="enrollmentId"
        :scroll="{ x: 1580 }"
        @change="handleTableChange"
      >
        <a-table-column title="考生" key="candidate" :width="190">
          <template #default="{ record }">
            <div class="candidate-name">{{ record.candidateName || '未命名考生' }}</div>
            <div class="candidate-account">{{ record.username || '—' }}</div>
          </template>
        </a-table-column>
        <a-table-column title="考号（登录账号）" data-index="username" key="username" :width="210">
          <template #default="{ record }">{{ record.username || '—' }}</template>
        </a-table-column>
        <a-table-column title="组织" data-index="organization" key="organization" :width="150">
          <template #default="{ record }">{{ record.organization || '—' }}</template>
        </a-table-column>
        <a-table-column title="账号状态" key="accountEnabled" :width="110">
          <template #default="{ record }">
            <a-tag :color="record.accountEnabled ? 'green' : 'red'">
              {{ record.accountEnabled ? '正常' : '已停用' }}
            </a-tag>
          </template>
        </a-table-column>
        <a-table-column title="登录状态" key="loggedIn" :width="120">
          <template #default="{ record }">
            <a-tag v-if="!snapshot.loginStatusAvailable" color="default">未知</a-tag>
            <a-tag v-else :color="record.loggedIn ? 'green' : 'default'">
              {{ record.loggedIn ? '已登录' : '未登录' }}
            </a-tag>
          </template>
        </a-table-column>
        <a-table-column title="考试状态" key="entered" :width="130">
          <template #default="{ record }">
            <a-tag :color="record.entered ? 'green' : 'default'">
              {{ record.entered ? '已进入' : '未进入' }}
            </a-tag>
          </template>
        </a-table-column>
        <a-table-column title="进入时间" key="enteredAt" :width="180">
          <template #default="{ record }">{{ formatTime(record.enteredAt) }}</template>
        </a-table-column>
        <a-table-column title="已开考时长" key="elapsed" :width="150">
          <template #default="{ record }">
            <span class="duration-value">{{ elapsedText(record) }}</span>
          </template>
        </a-table-column>
        <a-table-column title="剩余时间" key="remaining" :width="150">
          <template #default>
            <span :class="{ 'remaining-urgent': snapshot.examState === 'OPEN' && liveRemaining() <= 1800 }">
              {{ remainingText() }}
            </span>
          </template>
        </a-table-column>
        <a-table-column title="提交状态" key="submissionStatus" :width="150">
          <template #default="{ record }">
            <a-tag :color="submissionColor(record.submissionStatus)">{{ submissionLabel(record.submissionStatus) }}</a-tag>
          </template>
        </a-table-column>
        <a-table-column title="账号操作" key="actions" :width="350">
          <template #default="{ record }">
            <div class="account-actions">
              <a-button type="link" size="small" @click="openEditCandidate(record as ExamCandidateStatus)">
                <EditOutlined /> 编辑
              </a-button>
              <a-button type="link" size="small" @click="openChangePassword(record as ExamCandidateStatus)">
                <KeyOutlined /> 修改密码
              </a-button>
              <a-popconfirm
                title="确认将密码重置为 12345678 吗？"
                ok-text="确认重置"
                cancel-text="取消"
                @confirm="resetCandidatePassword(record as ExamCandidateStatus)"
              >
                <a-button type="link" size="small">重置密码</a-button>
              </a-popconfirm>
              <a-popconfirm
                :title="record.accountEnabled ? '停用后该考生将无法登录，确认吗？' : '确认重新启用该考生账号吗？'"
                ok-text="确认"
                cancel-text="取消"
                @confirm="toggleCandidateEnabled(record as ExamCandidateStatus)"
              >
                <a-button type="link" size="small">
                  {{ record.accountEnabled ? '停用' : '启用' }}
                </a-button>
              </a-popconfirm>
              <a-popconfirm
                title="确认将该考生移出本场考试吗？历史答题与成绩会保留。"
                ok-text="移出考试"
                cancel-text="取消"
                @confirm="removeCandidate(record as ExamCandidateStatus)"
              >
                <a-button type="link" size="small" danger><DeleteOutlined /> 删除</a-button>
              </a-popconfirm>
            </div>
          </template>
        </a-table-column>
        <template #emptyText>
          <a-empty description="当前批次暂无考生账号" />
        </template>
      </a-table>
    </a-card>

    <a-empty v-else-if="!campaignsLoading" description="请选择一个考试批次" class="empty-state" />

    <a-modal
      v-model:open="accountModalVisible"
      :title="accountModalTitle"
      :confirm-loading="accountSaving"
      ok-text="保存"
      cancel-text="取消"
      @ok="saveCandidateAccount"
    >
      <a-form ref="accountFormRef" :model="accountForm" :rules="accountRules" layout="vertical">
        <a-form-item label="考生姓名" name="nickname">
          <a-input v-model:value="accountForm.nickname" :maxlength="10" show-count placeholder="例如：张三" />
        </a-form-item>
        <a-form-item label="登录账号" name="username">
          <a-input v-model:value="accountForm.username" :maxlength="20" placeholder="例如：kaosheng04" />
        </a-form-item>
        <a-form-item label="邮箱（可选）" name="email">
          <a-input v-model:value="accountForm.email" :maxlength="100" placeholder="不填写则自动生成考试邮箱" />
        </a-form-item>
        <a-form-item v-if="!isEditingCandidate" label="初始密码" name="password">
          <a-input-password v-model:value="accountForm.password" :maxlength="20" />
        </a-form-item>
        <a-form-item label="组织/班级" name="organization">
          <a-input v-model:value="accountForm.organization" :maxlength="200" placeholder="可选" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="passwordModalVisible"
      title="修改考生密码"
      :confirm-loading="passwordSaving"
      ok-text="保存新密码"
      cancel-text="取消"
      @ok="saveCandidatePassword"
    >
      <a-alert
        type="info"
        show-icon
        :message="`正在修改 ${passwordCandidate?.username || '考生'} 的登录密码`"
        class="password-tip"
      />
      <a-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" layout="vertical">
        <a-form-item label="新密码" name="password">
          <a-input-password v-model:value="passwordForm.password" :maxlength="20" placeholder="请输入6～20位新密码" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="credentialModalVisible"
      :title="credential.title"
      :footer="null"
    >
      <a-alert type="info" show-icon :message="credential.tip" class="password-tip" />
      <div class="credential-box">
        <div class="credential-row">
          <span>姓名</span>
          <strong>{{ credential.nickname || '—' }}</strong>
        </div>
        <div class="credential-row">
          <span>登录账号</span>
          <strong>{{ credential.username || '—' }}</strong>
        </div>
        <div class="credential-row">
          <span>登录密码</span>
          <strong>{{ credential.password }}</strong>
        </div>
      </div>
      <div class="credential-actions">
        <a-button type="primary" @click="copyCredential">
          <template #icon><CopyOutlined /></template>
          复制账号信息
        </a-button>
        <a-button @click="credentialModalVisible = false">关闭</a-button>
      </div>
    </a-modal>

    <a-modal
      v-model:open="importModalVisible"
      title="批量导入考生"
      :width="680"
      :mask-closable="false"
      @cancel="importModalVisible = false"
    >
      <a-alert
        type="info"
        show-icon
        class="password-tip"
        message="按模板填写考生名单后一键导入：每位考生自动创建账号与独立的考生工作空间，并加入本场考试。"
      />
      <div class="import-steps">
        <div class="import-step-row">
          <span class="import-step-index">1</span>
          <a-button size="small" @click="downloadTemplate">
            <template #icon><DownloadOutlined /></template>
            下载导入模板
          </a-button>
          <span class="import-step-hint">模板列：姓名（必填）、账号（留空自动生成）、初始密码（留空为 12345678）、组织/单位（选填）</span>
        </div>
        <div class="import-step-row">
          <span class="import-step-index">2</span>
          <a-upload
            :max-count="1"
            accept=".xlsx,.xls"
            :before-upload="beforeImportUpload"
            :show-upload-list="false"
          >
            <a-button size="small">
              <template #icon><UploadOutlined /></template>
              选择 Excel 文件
            </a-button>
          </a-upload>
          <span v-if="importFile" class="import-file-name">{{ importFile.name }}</span>
        </div>
        <div class="import-step-row">
          <span class="import-step-index">3</span>
          <a-button type="primary" size="small" :loading="importing" :disabled="!importFile" @click="submitImport">
            开始导入
          </a-button>
          <span class="import-step-hint">单次最多 200 行；密码列留空时统一为 12345678</span>
        </div>
      </div>

      <template v-if="importResult">
        <a-divider />
        <div class="import-summary">
          <span>共解析 <b>{{ importResult.totalRows }}</b> 行</span>
          <span class="import-summary-success">成功 <b>{{ importResult.successCount }}</b></span>
          <span class="import-summary-failure">失败 <b>{{ importResult.failureCount }}</b></span>
        </div>
        <a-table
          v-if="importResult.outcomes.length"
          :data-source="importResult.outcomes"
          :columns="importOutcomeColumns"
          :pagination="false"
          size="small"
          row-key="rowNumber"
          :scroll="{ y: 240 }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'outcome'">
              <span v-if="(record as ExamCandidateImportOutcome).success" class="import-result-ok">
                <CheckCircleOutlined /> 导入成功
              </span>
              <span v-else class="import-result-fail">{{ (record as ExamCandidateImportOutcome).errorMessage || '导入失败' }}</span>
            </template>
          </template>
        </a-table>
      </template>

      <template #footer>
        <a-button type="primary" :disabled="importing" @click="importModalVisible = false">完成</a-button>
      </template>
    </a-modal>
  </main>
</template>

<style scoped lang="scss">
.candidate-status-page {
  min-height: 100%;
  padding: 28px;
  color: #1d2a3f;
  background: #f5f8fc;
}

.page-heading,
.toolbar-card,
.candidate-card {
  max-width: 1560px;
  margin-right: auto;
  margin-left: auto;
  border: 1px solid #dce7f5;
  background: #fff;
}

.candidate-search {
  width: min(320px, 60vw);
}

.account-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 2px;
}

.password-tip {
  margin-bottom: 18px;
}

.batch-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.batch-count {
  margin-right: 4px;
  color: #5f6f8d;
  font-size: 13px;
}

.credential-box {
  padding: 12px 14px;
  border: 1px solid #dce7f5;
  border-radius: 8px;
  background: #f8fbff;
}

.credential-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px dashed #e6eef8;
}

.credential-row:last-child {
  border-bottom: none;
}

.credential-row span {
  color: #5f6f8d;
}

.credential-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}

.page-heading {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 18px;
  padding: 22px 24px;
  border-radius: 8px;
}

.title-mark {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 52px;
  height: 52px;
  color: #fff;
  font-size: 25px;
  border-radius: 8px;
  background: #16a34a;
}

.heading-copy {
  flex: 1;
}

.heading-copy > span {
  color: #16a34a;
  font-size: 12px;
  font-weight: 700;
}

.heading-copy h1 {
  margin: 2px 0 3px;
  font-size: 23px;
}

.heading-copy p {
  margin: 0;
  color: #718096;
  font-size: 13px;
}

.heading-actions {
  display: flex;
  gap: 8px;
}

.toolbar-card {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  margin-bottom: 18px;
  padding: 16px 20px;
  border-radius: 8px;
}

.toolbar-label {
  color: #52627a;
  font-size: 13px;
  font-weight: 600;
}

.window-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #52627a;
  font-size: 12px;
}

.muted,
.candidate-account {
  color: #8a98ad;
}

.status-alert,
.candidate-card {
  max-width: 1560px;
  margin-right: auto;
  margin-bottom: 18px;
  margin-left: auto;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  max-width: 1560px;
  gap: 14px;
  margin: 0 auto 18px;
}

.stat-card :deep(.ant-card-body) {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 17px 18px;
}

.stat-icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 9px;
  font-size: 18px;
}

.stat-icon.total { color: #2563eb; background: #eaf2ff; }
.stat-icon.online { color: #16a34a; background: #eaf8ee; }
.stat-icon.entered { color: #7c3aed; background: #f1eaff; }
.stat-icon.submitted { color: #d97706; background: #fff4df; }

.stat-card span {
  display: block;
  color: #718096;
  font-size: 12px;
}

.stat-card strong {
  display: block;
  margin-top: 2px;
  color: #1d2a3f;
  font-size: 22px;
  line-height: 1.2;
}

.candidate-card {
  overflow: hidden;
  border-radius: 8px;
}

.candidate-name {
  color: #1d2a3f;
  font-weight: 600;
}

.candidate-account {
  margin-top: 3px;
  font-size: 12px;
}

.duration-value,
.remaining-urgent {
  font-variant-numeric: tabular-nums;
}

.remaining-urgent {
  color: #d46b08;
  font-weight: 700;
}

.empty-state {
  margin-top: 80px;
}

@media (max-width: 760px) {
  .candidate-status-page {
    padding: 14px;
  }

  .page-heading {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .heading-actions {
    width: 100%;
  }

  .heading-actions .ant-btn {
    flex: 1;
  }

  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .window-summary {
    width: 100%;
    flex-wrap: wrap;
  }
}

/* 批量导入弹窗 */
.import-steps {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 16px;
}

.import-step-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.import-step-index {
  display: grid;
  width: 24px;
  height: 24px;
  flex: none;
  border-radius: 50%;
  background: #e6f2ff;
  color: #2a72c5;
  font-size: 12px;
  font-weight: 700;
  place-items: center;
}

.import-step-hint {
  color: #8196ad;
  font-size: 12px;
  line-height: 1.5;
}

.import-file-name {
  color: #235d9f;
  font-size: 12px;
  font-weight: 600;
}

.import-summary {
  display: flex;
  gap: 18px;
  margin-bottom: 10px;
  color: #4f6785;
  font-size: 13px;

  b {
    font-size: 15px;
  }
}

.import-summary-success b {
  color: #1f9d63;
}

.import-summary-failure b {
  color: #d4513f;
}

.import-result-ok {
  color: #1f9d63;
}

.import-result-fail {
  color: #d4513f;
}
</style>
