<!-- eslint-disable vue/multi-word-component-names -->
<script setup lang="ts" name="LoginPage">
/**
 * 登录页面 — 支持多租户选择、审批等待状态
 *
 * @author huxuehao
 */
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { FormInstance, Rule } from 'ant-design-vue/es/form'
import type { LoginRequest, TenantInfo, PendingApprovalInfo } from '@/types'
import { useAccountStore } from '@/stores'
import { md5 } from 'js-md5'
import { RoutePaths } from '@/router/constants.ts'
import {
  ExclamationCircleOutlined,
  LeftOutlined,
  RightOutlined,
  UserOutlined,
  LockOutlined,
} from '@ant-design/icons-vue'
import heroImg from '@/assets/login-hero.png'

const router = useRouter()
const accountStore = useAccountStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

/** 是否显示租户选择弹窗 */
const showTenantModal = ref(false)
/** 待选择的租户列表 */
const pendingTenants = ref<TenantInfo[]>([])
/** 租户选择加载中 */
const selectingTenant = ref(false)

/** 登录被阻断 — 待审批视图 */
const showBlockedView = ref(false)
/** 待审批的申请列表 */
const pendingApprovals = ref<PendingApprovalInfo[]>([])

const formState = reactive<LoginRequest>({
  username: '',
  password: '',
})

const rules: Record<string, Rule[]> = {
  username: [
    { required: true, message: '请输入考号', trigger: 'blur' },
    { min: 4, max: 20, message: '考号长度为4-20个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为6-20个字符', trigger: 'blur' },
  ],
}

/**
 * 租户角色中文映射
 */
function tenantRoleLabel(role: string): string {
  const map: Record<string, string> = {
    TENANT_OWNER: '拥有者',
    TENANT_ADMIN: '管理员',
    TENANT_EDITOR: '编辑者',
    TENANT_VIEWER: '查看者'
  }
  return map[role] || role
}

/**
 * 租户角色颜色映射
 */
function tenantRoleColor(role: string): string {
  const map: Record<string, string> = {
    TENANT_OWNER: 'purple',
    TENANT_ADMIN: 'blue',
    TENANT_EDITOR: 'cyan',
    TENANT_VIEWER: 'default',
  }
  return map[role] || '#8c8c8c'
}

/**
 * 申请状态中文映射
 */
function statusLabel(status: string): string {
  const map: Record<string, string> = {
    PENDING: '待审批',
    APPROVED: '已通过',
    REJECTED: '已拒绝',
    CANCELLED: '已撤销'
  }
  return map[status] || status
}

const handleLogin = async () => {
  try {
    loading.value = true
    await formRef.value?.validate()

    const result = await accountStore.login({
      ...formState,
      password: md5(formState.password),
    })

    if (result.blocked) {
      // 登录被阻断，显示待审批视图
      pendingApprovals.value = result.pendingApprovals || []
      showBlockedView.value = true
      return
    }

    if (result.needSelectTenant) {
      // 需要选择租户，显示选择弹窗
      pendingTenants.value = result.tenants
      showTenantModal.value = true
      return
    }

    // 无需选择租户，直接完成登录
    completeLogin()
  } catch (error) {
    console.error('登录失败:', error)
  } finally {
    loading.value = false
  }
}

/**
 * 完成登录后的跳转
 */
function completeLogin() {
  message.success('登录成功')
  localStorage.removeItem('remember')
  localStorage.removeItem('username')
  location.reload()
  setTimeout(() => {
    router.push(RoutePaths.ROOT)
  }, 100)
}

/**
 * 选择租户并完成登录（携带账号密码和租户ID重新登录）
 */
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
      completeLogin()
    }
  } catch (error) {
    console.error('选择租户登录失败:', error)
    message.error('选择租户登录失败，请重试')
  } finally {
    selectingTenant.value = false
  }
}

/**
 * 返回登录表单
 */
function backToLogin() {
  showBlockedView.value = false
  showTenantModal.value = false
}

</script>

<template>
  <div class="login-page">
    <!-- 波纹动效层 -->
    <div class="ripple-layer" aria-hidden="true">
      <span class="ripple ripple-1"></span>
      <span class="ripple ripple-2"></span>
      <span class="ripple ripple-3"></span>
      <svg class="wave" viewBox="0 0 1440 110" preserveAspectRatio="none">
        <path d="M0,55 C240,95 480,15 720,55 C960,95 1200,15 1440,55 L1440,110 L0,110 Z"/>
        <path d="M0,55 C240,95 480,15 720,55 C960,95 1200,15 1440,55 L1440,110 L0,110 Z" transform="translate(1440,0)"/>
      </svg>
      <svg class="wave wave-2" viewBox="0 0 1440 70" preserveAspectRatio="none">
        <path d="M0,35 C240,60 480,10 720,35 C960,60 1200,10 1440,35 L1440,70 L0,70 Z"/>
        <path d="M0,35 C240,60 480,10 720,35 C960,60 1200,10 1440,35 L1440,70 L0,70 Z" transform="translate(1440,0)"/>
      </svg>
    </div>

    <!-- 顶部品牌 -->
    <header class="brand-header">
      <h1 class="brand-title">星图AI智能考试系统</h1>
      <p class="brand-subtitle">2026生成式人工智能系统应用实操考试</p>
    </header>

    <!-- 主体两栏 -->
    <main class="content">
      <!-- 左侧插画 -->
      <section class="hero" aria-hidden="true">
        <img :src="heroImg" alt="平台插画" />
      </section>

      <!-- 右侧登录卡 -->
      <section class="card-wrapper">
        <div class="card">
          <!-- 卡片右上角装饰 -->
          <div class="card-corner" aria-hidden="true">
            <svg viewBox="0 0 84 84">
              <g fill="none" stroke="#bfdbfe" stroke-width="1.4">
                <rect x="56" y="6" width="22" height="22"/>
                <rect x="62" y="12" width="10" height="10"/>
                <rect x="36" y="26" width="10" height="10"/>
                <rect x="46" y="36" width="6" height="6"/>
              </g>
            </svg>
          </div>

          <Transition name="auth-view" mode="out-in">
            <!-- 待审批阻断视图 -->
            <div v-if="showBlockedView" key="blocked" class="view-wrapper">
              <div class="blocked-container">
                <div class="blocked-header">
                  <ExclamationCircleOutlined class="blocked-icon" />
                  <h3 class="blocked-title">申请审批中</h3>
                  <p class="blocked-desc">
                    你已提交加入组织的申请，请等待管理员审批。
                  </p>
                </div>

                <div class="blocked-list">
                  <div
                    v-for="approval in pendingApprovals"
                    :key="approval.requestId"
                    class="blocked-item"
                  >
                    <div class="blocked-item-info">
                      <div class="blocked-item-name">{{ approval.tenantName }}</div>
                      <div class="blocked-item-meta">
                        <span class="blocked-item-code">{{ approval.tenantCode }}</span>
                      </div>
                    </div>
                    <ATag :bordered="false">{{ statusLabel(approval.status) }}</ATag>
                  </div>
                </div>

                <div class="blocked-actions">
                  <ASpace direction="vertical" style="width: 100%" :size="12">
                    <AButton
                      block
                      size="large"
                      @click="backToLogin"
                    >
                      返回登录
                    </AButton>
                  </ASpace>
                </div>
              </div>
            </div>

            <!-- 租户选择视图 -->
            <div v-else-if="showTenantModal" key="tenant" class="view-wrapper">
              <div class="tenant-select-view">
                <div class="tenant-select-back" @click="backToLogin">
                  <LeftOutlined />
                  <span>返回登录</span>
                </div>

                <div class="tenant-select-header">
                  <h3 class="tenant-select-title">选择组织</h3>
                  <p class="tenant-select-desc">您属于多个组织，请选择要进入的组织</p>
                </div>

                <div class="tenant-cards-scroll">
                  <div class="tenant-cards-list">
                    <div
                      v-for="tenant in pendingTenants"
                      :key="tenant.tenantId"
                      class="tenant-card"
                      :class="{ 'tenant-card-loading': selectingTenant }"
                      @click="handleSelectTenant(tenant)"
                    >
                      <div class="tenant-card-avatar">
                        {{ tenant.tenantName.charAt(0).toUpperCase() }}
                      </div>
                      <div class="tenant-card-body">
                        <div class="tenant-card-name" :title="tenant.tenantName">{{ tenant.tenantName }}</div>
                        <div class="tenant-card-code" :title="tenant.tenantCode">{{ tenant.tenantCode }}</div>
                      </div>
                      <div class="tenant-card-right">
                        <ATag class="tenant-card-role" :bordered="false" :color="tenantRoleColor(tenant.role)">{{ tenantRoleLabel(tenant.role) }}</ATag>
                        <div class="tenant-card-arrow">
                          <RightOutlined />
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 登录表单 -->
            <div v-else key="login" class="view-wrapper">
              <div class="tab-bar">
                <span class="tab active">考生登录</span>
              </div>

              <AForm
                ref="formRef"
                :model="formState"
                :rules="rules"
                layout="vertical"
                @finish="handleLogin"
              >
                <AFormItem name="username" class="auth-form-item">
                  <AInput
                    v-model:value="formState.username"
                    size="large"
                    placeholder="请输入考号"
                  >
                    <template #prefix>
                      <UserOutlined class="input-prefix-icon" />
                    </template>
                  </AInput>
                </AFormItem>

                <AFormItem name="password" class="auth-form-item">
                  <AInputPassword
                    v-model:value="formState.password"
                    size="large"
                    placeholder="请输入密码"
                  >
                    <template #prefix>
                      <LockOutlined class="input-prefix-icon" />
                    </template>
                  </AInputPassword>
                </AFormItem>

                <AFormItem>
                  <AButton
                    type="primary"
                    html-type="submit"
                    size="large"
                    :loading="loading"
                    block
                    class="btn-login"
                  >
                    登&nbsp;&nbsp;录
                  </AButton>
                </AFormItem>
              </AForm>
            </div>
          </Transition>
        </div>
      </section>
    </main>

    <!-- 底部提示 -->
    <footer class="footer-tip">
      推荐使用 Chrome、Edge、360 极速 等现代浏览器访问
    </footer>
  </div>
</template>

<style scoped lang="scss">
// ========== 页面背景 ==========
.login-page {
  position: relative;
  min-height: 100vh;
  width: 100%;
  overflow: hidden;
  background:
    radial-gradient(circle at 28% 48%, rgba(96, 165, 250, 0.28) 0%, transparent 46%),
    radial-gradient(circle at 78% 22%, rgba(37, 99, 235, 0.22) 0%, transparent 42%),
    radial-gradient(circle at 85% 82%, rgba(59, 130, 246, 0.22) 0%, transparent 48%),
    linear-gradient(135deg, #1e3a8a 0%, #275ee3 36%, #2563eb 62%, #3b82f6 100%);
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 24px 32px;
}

// ========== 波纹动效层 ==========
.ripple-layer {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 1;
  overflow: hidden;
}

.ripple {
  position: absolute;
  border-radius: 50%;
  border: 1.5px solid rgba(255, 255, 255, 0.18);
  animation: ripple-spread 7s ease-out infinite;
}
.ripple-1 { width: 120px; height: 120px; left: 6%;  top: 58%; animation-delay: 0s; }
.ripple-2 { width: 160px; height: 160px; left: 72%; top: 22%; animation-delay: 2.3s; }
.ripple-3 { width: 90px;  height: 90px;  left: 42%; top: 78%; animation-delay: 4.6s; }

@keyframes ripple-spread {
  0%   { transform: scale(0.25); opacity: 0; }
  15%  { opacity: 0.7; }
  100% { transform: scale(3.2); opacity: 0; }
}

.wave {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 200%;
  height: 110px;
  animation: wave-move 14s linear infinite;

  path { fill: rgba(255, 255, 255, 0.06); }
}
.wave-2 {
  height: 70px;
  opacity: 0.5;
  animation: wave-move 20s linear infinite reverse;
}

@keyframes wave-move {
  0%   { transform: translateX(0); }
  100% { transform: translateX(-50%); }
}

// ========== 顶部品牌 ==========
.brand-header {
  text-align: center;
  color: #fff;
  position: relative;
  z-index: 2;
  margin-bottom: 36px;
}

.brand-title {
  font-size: 30px;
  font-weight: 700;
  letter-spacing: 4px;
  color: #ffffff;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
  margin-top: 16px;
  margin-bottom: 8px;
}

.brand-subtitle {
  font-size: 13px;
  letter-spacing: 2px;
  color: rgba(255, 255, 255, 0.78);
  font-weight: 300;
  text-transform: uppercase;
}

// ========== 主体两栏 ==========
.content {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 80px;
  flex: 1;
  width: 100%;
  max-width: 1280px;
  padding: 12px;
}

.hero {
  flex: 1.1;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: center;

  img {
    width: 100%;
    max-width: 560px;
    height: auto;
    -webkit-mask-image: radial-gradient(ellipse 68% 68% at 50% 50%,
                                        #000 58%, rgba(0, 0, 0, 0.8) 75%, transparent 94%);
            mask-image: radial-gradient(ellipse 68% 68% at 50% 50%,
                                        #000 58%, rgba(0, 0, 0, 0.8) 75%, transparent 94%);
    filter: drop-shadow(0 10px 30px rgba(13, 35, 95, 0.22)) saturate(1.02);
  }
}

.card-wrapper {
  flex: 0 0 420px;
  max-width: 420px;
}

// ========== 卡片 ==========
.card {
  position: relative;
  background: #ffffff;
  border-radius: 14px;
  box-shadow:
    0 1px 3px rgba(15, 23, 42, 0.05),
    0 12px 36px rgba(15, 23, 42, 0.18);
  padding: 32px 36px 28px;
}

.card-corner {
  position: absolute;
  top: 0;
  right: 0;
  width: 84px;
  height: 84px;
  pointer-events: none;
  opacity: 0.85;

  svg { width: 100%; height: 100%; }
}

// ========== Tab 栏 ==========
.tab-bar {
  display: flex;
  gap: 28px;
  border-bottom: 1px solid #eef0f4;
  margin-bottom: 26px;
}

.tab {
  position: relative;
  font-size: 16px;
  font-weight: 600;
  color: #94a3b8;
  padding: 8px 4px 14px;
  cursor: pointer;
  user-select: none;

  &.active {
    color: #2563eb;

    &::after {
      content: "";
      position: absolute;
      left: 0;
      right: 0;
      bottom: -1px;
      height: 2px;
      background: #2563eb;
      border-radius: 2px;
    }
  }
}

// ========== 表单 ==========
.auth-form-item {
  margin-bottom: 6px;
}

// ========== 底部提示 ==========
.footer-tip {
  position: relative;
  z-index: 2;
  margin-top: 32px;
  padding: 8px 18px;
  background: rgba(255, 255, 255, 0.18);
  backdrop-filter: blur(6px);
  border-radius: 999px;
  color: rgba(255, 255, 255, 0.92);
  font-size: 13px;
  letter-spacing: 0.5px;
}

// ========== Ant Design 组件样式覆盖 ==========
// 输入框：底边框风格（无四周边框，仅底部线条）
:deep(.ant-input-affix-wrapper) {
  border: none;
  border-bottom: 1px solid #e5e7eb;
  border-radius: 0;
  background: transparent;
  padding: 14px 4px 12px;
  box-shadow: none;
  transition: border-color 0.2s ease;

  &:hover {
    border-bottom-color: #93c5fd;
  }
}

:deep(.ant-input-affix-wrapper-focused),
:deep(.ant-input-affix-wrapper:focus-within) {
  border-bottom-color: #2563eb;
  box-shadow: none;
}

:deep(.ant-input) {
  background: transparent;
  border: none;
  font-size: 15px;
  color: #1f2937;

  &::placeholder {
    color: #cbd5e1;
    font-weight: 300;
  }
}

:deep(.ant-input-prefix) {
  color: #94a3b8;
  margin-right: 10px;

  .input-prefix-icon {
    font-size: 18px;
  }
}

:deep(.ant-input-suffix) {
  color: #94a3b8;
}

// 登录按钮：蓝色渐变
:deep(.btn-login.ant-btn-primary) {
  height: 48px;
  border: 0;
  border-radius: 6px;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 8px;
  color: #ffffff;
  background: linear-gradient(135deg, #93c5fd 0%, #60a5fa 100%);
  box-shadow: 0 6px 18px rgba(96, 165, 250, 0.4);
  transition: transform 0.15s ease, box-shadow 0.2s ease;

  &:hover,
  &:focus {
    transform: translateY(-1px);
    background: linear-gradient(135deg, #93c5fd 0%, #60a5fa 100%);
    box-shadow: 0 8px 22px rgba(96, 165, 250, 0.55);
    color: #ffffff;
  }

  &:active {
    transform: translateY(0);
  }
}

// Checkbox 强调色
:deep(.ant-checkbox-checked .ant-checkbox-inner) {
  background-color: #2563eb;
  border-color: #2563eb;
}

// ========== 视图切换过渡动画 ==========
.auth-view-enter-active,
.auth-view-leave-active {
  transition: opacity 0.35s cubic-bezier(0.4, 0, 0.2, 1),
              transform 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}

.auth-view-enter-from {
  opacity: 0;
  transform: translateY(16px) scale(0.97);
}

.auth-view-leave-to {
  opacity: 0;
  transform: translateY(-12px) scale(0.98);
}

.view-wrapper {
  width: 100%;
}

// ========== 阻断视图样式 ==========
.blocked-container {
  padding: 8px 0;
}

.blocked-header {
  text-align: center;
  margin-bottom: 24px;
}

.blocked-icon {
  font-size: 48px;
  color: var(--warning-color, #faad14);
  margin-bottom: 12px;
}

.blocked-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary, #1f1f1f);
  margin: 0 0 8px 0;
}

.blocked-desc {
  font-size: 14px;
  color: var(--text-secondary, #666);
  margin: 0;
  line-height: 1.6;
}

.blocked-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 20px;
}

.blocked-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: var(--bg-secondary, #fafafa);
  border: 1px solid var(--border-color, #e8e8e8);
  border-radius: 8px;
  transition: border-color 0.2s;

  &:hover {
    border-color: var(--primary-color, #1677ff);
  }
}

.blocked-item-info {
  flex: 1;
  min-width: 0;
}

.blocked-item-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary, #1f1f1f);
  margin-bottom: 4px;
}

.blocked-item-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.blocked-item-code {
  font-size: 12px;
  color: var(--text-tertiary, #999);
}

.blocked-actions {
  text-align: center;
}

// ========== 租户选择视图 ==========
.tenant-select-view {
  padding: 4px 0;
}

.tenant-select-back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #999;
  cursor: pointer;
  padding: 6px 2px;
  margin-bottom: 20px;
  transition: color 0.25s ease;
  user-select: none;

  &:hover {
    color: #1677ff;
  }

  .anticon {
    font-size: 13px;
    transition: transform 0.25s ease;
  }

  &:hover .anticon {
    transform: translateX(-3px);
  }
}

.tenant-select-header {
  text-align: center;
  margin-bottom: 28px;
}

.tenant-select-title {
  font-size: 22px;
  font-weight: 600;
  color: #1f1f1f;
  margin: 0 0 10px 0;
  letter-spacing: 0.5px;
}

.tenant-select-desc {
  font-size: 14px;
  color: #999;
  margin: 0;
  line-height: 1.5;
}

.tenant-cards-scroll {
  max-height: 380px;
  overflow-y: auto;
  padding-right: 6px;
  margin-right: -6px;

  &::-webkit-scrollbar {
    width: 4px;
  }

  &::-webkit-scrollbar-track {
    background: transparent;
    border-radius: 2px;
  }

  &::-webkit-scrollbar-thumb {
    background: #d9d9d9;
    border-radius: 2px;
    transition: background 0.2s;
  }

  &::-webkit-scrollbar-thumb:hover {
    background: #bbb;
  }

  scrollbar-width: thin;
  scrollbar-color: #d9d9d9 transparent;
}

.tenant-cards-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.tenant-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 14px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    border-radius: 14px;
    background: linear-gradient(135deg, rgba(22, 119, 255, 0.04), rgba(22, 119, 255, 0.01));
    opacity: 0;
    transition: opacity 0.35s ease;
  }

  &:hover {
    border-color: #b8d4ff;
    transform: translateY(-3px);

    &::before {
      opacity: 1;
    }

    .tenant-card-avatar {
      background: linear-gradient(135deg, #1677ff, #4096ff);
      color: #fff;
      transform: scale(1.05);
    }

    .tenant-card-arrow {
      opacity: 1;
      transform: translateX(0);
    }

    .tenant-card-name {
      color: #1677ff;
    }
  }

  &:active {
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(22, 119, 255, 0.06);
    transition: all 0.15s ease;
  }

  &.tenant-card-loading {
    pointer-events: none;
    opacity: 0.7;
  }
}

.tenant-card-avatar {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: linear-gradient(135deg, #e8f2ff, #d6e8ff);
  color: #1677ff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 600;
  flex-shrink: 0;
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}

.tenant-card-body {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.tenant-card-name {
  font-size: 15px;
  font-weight: 600;
  color: #1f1f1f;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: color 0.35s ease;
  line-height: 1.4;
}

.tenant-card-code {
  font-size: 12px;
  color: #bbb;
  margin-top: 3px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.3;
}

.tenant-card-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.tenant-card-role {
  font-size: 11px;
  border-radius: 6px;
  padding: 0 8px;
  line-height: 22px;
}

.tenant-card-arrow {
  color: #1677ff;
  font-size: 12px;
  opacity: 0;
  transform: translateX(-8px);
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  align-items: center;
}

// ========== 响应式 ==========
@media (max-width: 1024px) {
  .content {
    flex-direction: column;
    gap: 28px;
  }

  .hero {
    display: none;
  }

  .card-wrapper {
    flex: 1;
    max-width: 440px;
    width: 100%;
  }

  .brand-title {
    font-size: 22px;
    letter-spacing: 2px;
  }

  .brand-subtitle {
    letter-spacing: 3px;
    font-size: 11px;
  }
}

@media (max-width: 480px) {
  .login-page {
    padding: 24px 16px;
  }

  .card {
    padding: 26px 22px 22px;
  }

  .brand-title {
    font-size: 18px;
    letter-spacing: 1px;
  }
}
</style>
