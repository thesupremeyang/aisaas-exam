<!--
  用户管理（平台管理员专用）

  平台级账号管理：新增账号（可授予平台管理员身份）、设为/取消管理员、
  重置密码、禁用/启用、删除。内置 admin 账号受后端保护不可删除/禁用/取消管理员。

  @author workbuddy
-->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { ReloadOutlined, SearchOutlined, UserAddOutlined, KeyOutlined } from '@ant-design/icons-vue'
import { useAccountStore } from '@/stores'
import { isPlatformAdminUsername } from '@/utils/platformAdmin'
import {
  createPlatformAccount,
  listPlatformAccounts,
  removePlatformAccounts,
  resetPlatformAccountPassword,
  setPlatformAdminFlag,
  togglePlatformAccountEnabled,
} from '@/api/account'
import type { AccountVO } from '@/types'

const accountStore = useAccountStore()

const accounts = ref<AccountVO[]>([])
const loading = ref(false)
const keyword = ref('')
const creating = ref(false)
const resetting = ref(false)

const createVisible = ref(false)
const createForm = ref({
  username: '',
  nickname: '',
  password: '',
  email: '',
  platformAdmin: false,
})

const resetVisible = ref(false)
const resetTarget = ref<AccountVO | null>(null)
const resetPassword = ref('')

/** 当前操作者是否平台管理员（页面仅管理员入口可见，二次防御） */
const isPlatformAdmin = computed(() => isPlatformAdminUsername(accountStore.userInfo?.username))

/** 内置管理员账号：后端保护不可删除/禁用/取消管理员 */
function isBuiltInAdmin(row: AccountVO): boolean {
  return row.username?.toLowerCase() === 'admin'
}

const filteredAccounts = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return accounts.value
  return accounts.value.filter((row) =>
    [row.username, row.nickname, row.email].some((value) => value?.toLowerCase().includes(kw)),
  )
})

async function loadAccounts(silent = false) {
  if (!silent) loading.value = true
  try {
    const res = await listPlatformAccounts()
    accounts.value = res.data.data || []
  } catch (error) {
    console.error(error)
    if (!silent) message.error('账号列表加载失败')
  } finally {
    if (!silent) loading.value = false
  }
}

function openCreate() {
  createForm.value = { username: '', nickname: '', password: '', email: '', platformAdmin: false }
  createVisible.value = true
}

async function submitCreate() {
  const form = createForm.value
  if (!form.username.trim() || !form.nickname.trim() || !form.password) {
    message.warning('用户名、昵称、初始密码不能为空')
    return
  }
  if (form.password.length < 6) {
    message.warning('初始密码至少 6 位')
    return
  }
  creating.value = true
  try {
    await createPlatformAccount({
      username: form.username.trim(),
      nickname: form.nickname.trim(),
      password: form.password,
      email: form.email.trim() || undefined,
      platformAdmin: form.platformAdmin,
    })
    message.success(`账号 ${form.username.trim()} 创建成功`)
    createVisible.value = false
    await loadAccounts(true)
  } catch (error) {
    console.error(error)
    message.error('账号创建失败（用户名或邮箱可能已存在）')
  } finally {
    creating.value = false
  }
}

function openReset(row: AccountVO) {
  resetTarget.value = row
  resetPassword.value = ''
  resetVisible.value = true
}

async function submitReset() {
  if (!resetTarget.value || !resetPassword.value) {
    message.warning('新密码不能为空')
    return
  }
  if (resetPassword.value.length < 6) {
    message.warning('新密码至少 6 位')
    return
  }
  resetting.value = true
  try {
    await resetPlatformAccountPassword(resetTarget.value.id, resetPassword.value)
    message.success(`账号 ${resetTarget.value.username} 密码已重置`)
    resetVisible.value = false
  } catch (error) {
    console.error(error)
    message.error('密码重置失败')
  } finally {
    resetting.value = false
  }
}

async function toggleEnabled(row: AccountVO, enabled: boolean) {
  try {
    await togglePlatformAccountEnabled(row.id, enabled)
    message.success(`账号 ${row.username} 已${enabled ? '启用' : '禁用'}`)
    await loadAccounts(true)
  } catch (error) {
    console.error(error)
    message.error('操作失败')
  }
}

async function setAdmin(row: AccountVO, flag: boolean) {
  try {
    await setPlatformAdminFlag(row.id, flag)
    message.success(`账号 ${row.username} 已${flag ? '设为平台管理员' : '取消平台管理员'}`)
    if (row.username === accountStore.userInfo?.username) {
      // 修改自身身份后需重新登录使 token 中的标记生效
      Modal.info({
        title: '身份已变更',
        content: '平台管理员身份随登录令牌生效，请退出后重新登录。',
      })
    }
    await loadAccounts(true)
  } catch (error) {
    console.error(error)
    message.error('操作失败')
  }
}

function removeAccount(row: AccountVO) {
  Modal.confirm({
    title: `删除账号 ${row.username}？`,
    content: '删除后该账号无法登录，且不可恢复。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await removePlatformAccounts([row.id])
        message.success('账号已删除')
        await loadAccounts(true)
      } catch (error) {
        console.error(error)
        message.error('删除失败')
      }
    },
  })
}

onMounted(() => {
  void loadAccounts()
})
</script>

<template>
  <div class="user-manage-page">
    <a-alert type="info" show-icon class="page-hint">
      <template #message>
        平台管理员可访问全部租户数据与管理功能（考试管理、答卷管理、AI 阅卷、用户管理等）。
        身份变更后需重新登录生效。
      </template>
    </a-alert>

    <div class="toolbar">
      <a-input
        v-model:value="keyword"
        placeholder="搜索用户名 / 昵称 / 邮箱"
        class="search-input"
        allow-clear
      >
        <template #prefix><SearchOutlined /></template>
      </a-input>
      <a-button :loading="loading" @click="loadAccounts()">
        <template #icon><ReloadOutlined /></template>
        刷新
      </a-button>
      <a-button type="primary" @click="openCreate">
        <template #icon><UserAddOutlined /></template>
        新增账号
      </a-button>
    </div>

    <a-table
      :data-source="filteredAccounts"
      :loading="loading"
      :row-key="(row: AccountVO) => String(row.id)"
      :pagination="{ pageSize: 20, showSizeChanger: false }"
    >
      <a-table-column title="用户名" data-index="username" :width="160">
        <template #default="{ record }">
          <span class="username">{{ record.username }}</span>
        </template>
      </a-table-column>
      <a-table-column title="昵称" data-index="nickname" :width="140" />
      <a-table-column title="邮箱" data-index="email" :width="200">
        <template #default="{ record }">{{ record.email || '—' }}</template>
      </a-table-column>
      <a-table-column title="身份" :width="130">
        <template #default="{ record }">
          <a-tag v-if="record.platformAdmin" color="blue">平台管理员</a-tag>
          <a-tag v-else color="default">普通账号</a-tag>
        </template>
      </a-table-column>
      <a-table-column title="状态" :width="100">
        <template #default="{ record }">
          <a-tag :color="record.enabled ? 'success' : 'error'">
            {{ record.enabled ? '正常' : '已禁用' }}
          </a-tag>
        </template>
      </a-table-column>
      <a-table-column title="创建时间" data-index="createdAt" :width="160">
        <template #default="{ record }">{{ record.createdAt || '—' }}</template>
      </a-table-column>
      <a-table-column title="操作" :width="330">
        <template #default="{ record }">
          <a-space>
            <a-button size="small" type="link" @click="openReset(record)">
              <template #icon><KeyOutlined /></template>
              重置密码
            </a-button>
            <a-popconfirm
              :title="record.platformAdmin ? '取消该账号的平台管理员身份？' : '将该账号设为平台管理员？'"
              @confirm="setAdmin(record, !record.platformAdmin)"
            >
              <a-button
                size="small"
                type="link"
                :disabled="isBuiltInAdmin(record) && record.platformAdmin"
              >
                {{ record.platformAdmin ? '取消管理员' : '设为管理员' }}
              </a-button>
            </a-popconfirm>
            <a-popconfirm
              :title="record.enabled ? '禁用后该账号将无法登录，确认？' : '启用该账号？'"
              @confirm="toggleEnabled(record, !record.enabled)"
            >
              <a-button size="small" type="link" :disabled="isBuiltInAdmin(record)">
                {{ record.enabled ? '禁用' : '启用' }}
              </a-button>
            </a-popconfirm>
            <a-popconfirm title="删除后不可恢复，确认删除？" @confirm="removeAccount(record)">
              <a-button size="small" type="link" danger :disabled="isBuiltInAdmin(record)">
                删除
              </a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </a-table-column>
    </a-table>

    <a-modal
      v-model:open="createVisible"
      title="新增账号"
      :confirm-loading="creating"
      ok-text="创建"
      cancel-text="取消"
      @ok="submitCreate"
    >
      <a-form layout="vertical" class="create-form">
        <a-form-item label="用户名（登录账号，创建后不可改）" required>
          <a-input v-model:value="createForm.username" placeholder="例如：exam-admin" :maxlength="50" />
        </a-form-item>
        <a-form-item label="昵称" required>
          <a-input v-model:value="createForm.nickname" placeholder="显示名称" :maxlength="50" />
        </a-form-item>
        <a-form-item label="初始密码（至少 6 位，创建后可在列表中重置）" required>
          <a-input-password v-model:value="createForm.password" placeholder="初始登录密码" />
        </a-form-item>
        <a-form-item label="邮箱（可选，可用于登录）">
          <a-input v-model:value="createForm.email" placeholder="name@example.com" />
        </a-form-item>
        <a-form-item label="账号身份">
          <a-radio-group v-model:value="createForm.platformAdmin">
            <a-radio :value="false">普通账号</a-radio>
            <a-radio :value="true">平台管理员（可访问全部租户数据与管理功能）</a-radio>
          </a-radio-group>
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="resetVisible"
      :title="`重置密码：${resetTarget?.username || ''}`"
      :confirm-loading="resetting"
      ok-text="重置"
      cancel-text="取消"
      @ok="submitReset"
    >
      <a-form layout="vertical">
        <a-form-item label="新密码（至少 6 位，重置后请告知该用户）" required>
          <a-input-password v-model:value="resetPassword" placeholder="新密码" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped lang="scss">
.user-manage-page {
  padding: 16px 20px;
}

.page-hint {
  margin-bottom: 12px;
}

.toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;

  .search-input {
    width: 280px;
  }
}

.username {
  font-weight: 500;
}

.create-form {
  margin-top: 4px;
}
</style>
