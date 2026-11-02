<!-- eslint-disable vue/multi-word-component-names -->
<script setup lang="ts" name="LoginV5Page">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { FormInstance, Rule } from 'ant-design-vue/es/form'
import {
  ExclamationCircleOutlined,
  LeftOutlined,
  RightOutlined,
} from '@ant-design/icons-vue'
import { md5 } from 'js-md5'
import { useAccountStore } from '@/stores'
import { RoutePaths } from '@/router/constants.ts'

// 考试部署默认关闭自助注册（.env.main: VITE_REGISTER_ENABLED=false）
const registerEnabled = import.meta.env.VITE_REGISTER_ENABLED === 'true'
import type { LoginRequest, PendingApprovalInfo, TenantInfo } from '@/types'

interface LoginForm extends LoginRequest {
  remember: boolean
}

const router = useRouter()
const accountStore = useAccountStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

const showTenantModal = ref(false)
const pendingTenants = ref<TenantInfo[]>([])
const selectingTenant = ref(false)

const showBlockedView = ref(false)
const pendingApprovals = ref<PendingApprovalInfo[]>([])

const formState = reactive<LoginForm>({
  username: '',
  password: '',
  remember: false,
})

const rules: Record<string, Rule[]> = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 20, message: '用户名长度为4-20个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为6-20个字符', trigger: 'blur' },
  ],
}

function tenantRoleLabel(role: string): string {
  const map: Record<string, string> = {
    TENANT_OWNER: '拥有者',
    TENANT_ADMIN: '管理员',
    TENANT_EDITOR: '编辑者',
    TENANT_VIEWER: '查看者',
  }
  return map[role] || role
}

function tenantRoleColor(role: string): string {
  const map: Record<string, string> = {
    TENANT_OWNER: 'purple',
    TENANT_ADMIN: 'blue',
    TENANT_EDITOR: 'cyan',
    TENANT_VIEWER: 'default',
  }
  return map[role] || '#8c8c8c'
}

function statusLabel(status: string): string {
  const map: Record<string, string> = {
    PENDING: '待审批',
    APPROVED: '已通过',
    REJECTED: '已拒绝',
    CANCELLED: '已撤销',
  }
  return map[status] || status
}

const handleLogin = async () => {
  try {
    loading.value = true
    await formRef.value?.validate()

    const { remember, ...loginData } = formState
    const result = await accountStore.login({ ...loginData, password: md5(loginData.password) })

    if (result.blocked) {
      pendingApprovals.value = result.pendingApprovals || []
      showBlockedView.value = true
      return
    }

    if (result.needSelectTenant) {
      pendingTenants.value = result.tenants
      showTenantModal.value = true
      return
    }

    completeLogin(remember)
  } catch (error) {
    console.error('登录失败:', error)
  } finally {
    loading.value = false
  }
}

function completeLogin(remember: boolean) {
  message.success('登录成功')
  if (remember) {
    localStorage.setItem('remember', 'true')
    localStorage.setItem('username', formState.username)
  } else {
    localStorage.removeItem('remember')
    localStorage.removeItem('username')
  }
  location.reload()
  setTimeout(() => {
    router.push(RoutePaths.ROOT)
  }, 100)
}

async function handleSelectTenant(tenant: TenantInfo) {
  try {
    selectingTenant.value = true
    const result = await accountStore.login({
      username: formState.username,
      password: md5(formState.password),
      tenantId: tenant.tenantId,
    })
    showTenantModal.value = false
    if (!result.blocked) {
      completeLogin(formState.remember)
    }
  } catch (error) {
    console.error('选择租户登录失败:', error)
    message.warning('选择租户登录失败，请重试')
  } finally {
    selectingTenant.value = false
  }
}

function backToLogin() {
  showBlockedView.value = false
  showTenantModal.value = false
}

const goToRegister = () => {
  router.push(RoutePaths.REGISTER)
}

const goToForgotPassword = () => {
  router.push(RoutePaths.FORGOT_PASSWORD)
}
</script>

<template>
  <main class="login-page">
    <div class="login-container">
      <!-- 登录卡片 -->
      <div class="login-card">
        <!-- 品牌区域 -->
        <div class="brand-section">
          <div class="brand-icon">
            <svg viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect width="48" height="48" rx="12" fill="url(#brand-grad)" />
              <path d="M14 28V18L24 12L34 18V28L24 34L14 28Z" stroke="white" stroke-width="2.2" stroke-linejoin="round" />
              <circle cx="24" cy="23" r="4" fill="white" />
              <defs>
                <linearGradient id="brand-grad" x1="0" y1="0" x2="48" y2="48">
                  <stop stop-color="#2563EB" />
                  <stop offset="1" stop-color="#4F46E5" />
                </linearGradient>
              </defs>
            </svg>
          </div>
      <h1 class="brand-title">生成式人工智能系统应用考试系统平台</h1>
          <p class="brand-subtitle">专业 · 严谨 · 智能</p>
        </div>

        <!-- 视图切换区域 -->
        <Transition name="view-fade" mode="out-in">
          <!-- 待审批阻断视图 -->
          <div v-if="showBlockedView" key="blocked" class="state-view">
            <button class="ghost-back" type="button" @click="backToLogin">
              <LeftOutlined />
              返回登录
            </button>
            <div class="state-header">
              <ExclamationCircleOutlined class="state-icon" />
              <h3>申请审批中</h3>
              <p>你已提交加入组织的申请，请等待管理员审批。</p>
            </div>

            <div class="blocked-list">
              <div v-for="approval in pendingApprovals" :key="approval.requestId" class="blocked-item">
                <div>
                  <strong>{{ approval.tenantName }}</strong>
                  <span>{{ approval.tenantCode }}</span>
                </div>
                <ATag :bordered="false">{{ statusLabel(approval.status) }}</ATag>
              </div>
            </div>

            <AButton
              v-if="registerEnabled"
              type="primary"
              block
              size="large"
              @click="goToRegister"
            >
              注册新租户
            </AButton>
          </div>

          <!-- 租户选择视图 -->
          <div v-else-if="showTenantModal" key="tenant" class="state-view">
            <button class="ghost-back" type="button" @click="backToLogin">
              <LeftOutlined />
              返回登录
            </button>
            <div class="state-header">
              <h3>选择登录空间</h3>
              <p>你的账号已加入多个空间，请选择本次进入的工作空间。</p>
            </div>

            <div class="tenant-cards-scroll">
              <button
                v-for="tenant in pendingTenants"
                :key="tenant.tenantId"
                class="tenant-card"
                :class="{ 'tenant-card-loading': selectingTenant }"
                type="button"
                :disabled="selectingTenant"
                @click="handleSelectTenant(tenant)"
              >
                <span class="tenant-card-avatar">
                  {{ (tenant.tenantName || tenant.tenantCode || '?').charAt(0).toUpperCase() }}
                </span>
                <span class="tenant-card-body">
                  <strong>{{ tenant.tenantName }}</strong>
                  <small>{{ tenant.tenantCode }}</small>
                </span>
                <span class="tenant-card-right">
                  <ATag :color="tenantRoleColor(tenant.role)" :bordered="false">
                    {{ tenantRoleLabel(tenant.role) }}
                  </ATag>
                  <RightOutlined />
                </span>
              </button>
            </div>
          </div>

          <!-- 登录表单 -->
          <div v-else key="login" class="login-view">
            <div class="login-heading">用户登录</div>

            <AForm
              ref="formRef"
              :model="formState"
              :rules="rules"
              layout="vertical"
              class="login-form"
              @finish="handleLogin"
            >
              <AFormItem name="username">
                <AInput
                  v-model:value="formState.username"
                  size="large"
                  placeholder="请输入用户名或邮箱"
                  autocomplete="username"
                >
                  <template #prefix>
                    <span class="input-prefix-icon">👤</span>
                  </template>
                </AInput>
              </AFormItem>

              <AFormItem name="password">
                <AInputPassword
                  v-model:value="formState.password"
                  size="large"
                  placeholder="请输入密码"
                  autocomplete="current-password"
                >
                  <template #prefix>
                    <span class="input-prefix-icon">🔒</span>
                  </template>
                </AInputPassword>
              </AFormItem>

              <div class="form-tools">
                <ACheckbox v-model:checked="formState.remember">记住账号</ACheckbox>
                <button type="button" class="link-button" @click="goToForgotPassword">
                  忘记密码？
                </button>
              </div>

              <AButton
                type="primary"
                html-type="submit"
                block
                size="large"
                :loading="loading"
                class="login-submit"
              >
                登 录
              </AButton>
            </AForm>

            <div v-if="registerEnabled" class="register-row">
              <span>还没有账号？</span>
              <button type="button" class="link-button" @click="goToRegister">
                立即注册
              </button>
            </div>
          </div>
        </Transition>
      </div>

      <!-- 底部版权 -->
      <div class="footer-text">
        Copyright &copy; {{ new Date().getFullYear() }} 生成式人工智能系统应用考试系统平台
      </div>
    </div>
  </main>
</template>

<style scoped lang="scss">
/* ========== 页面容器 ========== */
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(ellipse at 20% 50%, rgb(37 99 235 / 4%) 0%, transparent 60%),
    radial-gradient(ellipse at 80% 20%, rgb(79 70 229 / 3%) 0%, transparent 50%),
    linear-gradient(180deg, #f8fafc 0%, #f1f5f9 50%, #e8edf3 100%);
  padding: 24px;
}

.login-container {
  width: 100%;
  max-width: 432px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 28px;
}

/* ========== 登录卡片 ========== */
.login-card {
  width: 100%;
  background: #fff;
  border-radius: 16px;
  padding: 40px 36px 36px;
  box-shadow:
    0 1px 3px rgb(0 0 0 / 4%),
    0 4px 16px rgb(0 0 0 / 4%),
    0 20px 48px rgb(37 99 235 / 6%);
}

/* ========== 品牌区域 ========== */
.brand-section {
  text-align: center;
  margin-bottom: 32px;
}

.brand-icon {
  margin-bottom: 16px;

  svg {
    width: 48px;
    height: 48px;
  }
}

.brand-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #1e293b;
  line-height: 1.5;
  letter-spacing: 0.5px;
}

.brand-subtitle {
  margin: 8px 0 0;
  font-size: 13px;
  color: #94a3b8;
  letter-spacing: 2px;
}

/* ========== 登录标题 ========== */
.login-heading {
  text-align: center;
  font-size: 15px;
  font-weight: 600;
  color: #475569;
  margin-bottom: 20px;
}

/* ========== 表单样式 ========== */
.login-form {
  :deep(.ant-form-item) {
    margin-bottom: 16px;
  }

  :deep(.ant-form-item-label > label) {
    display: none;
  }

  :deep(.ant-input),
  :deep(.ant-input-affix-wrapper) {
    height: 46px;
    border-radius: 8px;
    border-color: #e2e8f0;
    background: #f8fafc;
    font-size: 14px;
    transition:
      border-color 0.2s ease,
      box-shadow 0.2s ease;

    &:hover {
      border-color: #93c5fd;
    }

    &:focus,
    &.ant-input-affix-wrapper-focused {
      border-color: #3b82f6;
      box-shadow: 0 0 0 3px rgb(59 130 246 / 12%);
      background: #fff;
    }
  }

  :deep(.ant-input-affix-wrapper .ant-input) {
    height: auto;
    border: 0;
    box-shadow: none;
    background: transparent;
  }

  :deep(.ant-input-prefix) {
    margin-right: 10px;
  }
}

.input-prefix-icon {
  font-size: 16px;
  opacity: 0.6;
}

.form-tools {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 4px 0 20px;
  font-size: 13px;
  color: #94a3b8;

  :deep(.ant-checkbox-wrapper) {
    font-size: 13px;
    color: #64748b;
  }
}

.link-button {
  border: 0;
  padding: 0;
  background: transparent;
  color: #3b82f6;
  font-size: 13px;
  cursor: pointer;

  &:hover {
    color: #2563eb;
    text-decoration: underline;
  }
}

.login-submit {
  height: 46px;
  border: 0;
  border-radius: 8px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 4px;
  background: #2563eb;
  box-shadow: 0 4px 14px rgb(37 99 235 / 28%);
  transition:
    background 0.2s ease,
    box-shadow 0.2s ease;

  &:hover {
    background: #1d4ed8;
    box-shadow: 0 6px 20px rgb(37 99 235 / 36%);
  }
}

.register-row {
  margin-top: 22px;
  text-align: center;
  font-size: 13px;
  color: #94a3b8;

  .link-button {
    font-size: 13px;
    font-weight: 600;
  }
}

/* ========== 状态视图（审批中 / 租户选择） ========== */
.state-view {
  min-height: 320px;
}

.ghost-back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 0;
  padding: 0;
  background: transparent;
  color: #3b82f6;
  font-size: 13px;
  cursor: pointer;
  margin-bottom: 22px;

  &:hover {
    color: #2563eb;
  }
}

.state-header {
  text-align: center;
  margin-bottom: 24px;

  h3 {
    margin: 0;
    color: #1e293b;
    font-size: 20px;
    font-weight: 700;
  }

  p {
    margin: 10px 0 0;
    color: #94a3b8;
    font-size: 14px;
    line-height: 1.6;
  }
}

.state-icon {
  margin-bottom: 14px;
  color: #f59e0b;
  font-size: 44px;
}

.blocked-list {
  display: grid;
  gap: 10px;
  margin-bottom: 18px;
}

.blocked-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #f8fafc;

  strong,
  span {
    display: block;
  }

  strong {
    color: #1e293b;
    font-size: 14px;
  }

  span {
    margin-top: 3px;
    color: #94a3b8;
    font-size: 12px;
  }
}

.tenant-cards-scroll {
  max-height: 380px;
  overflow-y: auto;
  display: grid;
  gap: 10px;

  &::-webkit-scrollbar {
    width: 4px;
  }

  &::-webkit-scrollbar-thumb {
    background: #cbd5e1;
    border-radius: 999px;
  }
}

.tenant-card {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #f8fafc;
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    background 0.2s ease;

  &:hover {
    background: #fff;
    border-color: #93c5fd;
    box-shadow: 0 4px 14px rgb(37 99 235 / 8%);
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.65;
  }
}

.tenant-card-loading {
  pointer-events: none;
}

.tenant-card-avatar {
  width: 42px;
  height: 42px;
  flex: 0 0 42px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  color: #2563eb;
  background: #eff6ff;
  font-size: 17px;
  font-weight: 750;
}

.tenant-card-body {
  min-width: 0;
  flex: 1;

  strong,
  small {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: #1e293b;
    font-size: 14px;
  }

  small {
    margin-top: 3px;
    color: #94a3b8;
    font-size: 12px;
  }
}

.tenant-card-right {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #2563eb;
  flex-shrink: 0;
}

/* ========== 视图切换动画 ========== */
.view-fade-enter-active,
.view-fade-leave-active {
  transition:
    opacity 0.2s ease;
}

.view-fade-enter-from,
.view-fade-leave-to {
  opacity: 0;
}

/* ========== 底部版权 ========== */
.footer-text {
  font-size: 12px;
  color: #94a3b8;
  text-align: center;
  user-select: none;
}

/* ========== 响应式 ========== */
@media (max-width: 480px) {
  .login-page {
    padding: 16px;
  }

  .login-card {
    padding: 32px 24px 28px;
    border-radius: 12px;
  }

  .brand-title {
    font-size: 16px;
  }
}
</style>
