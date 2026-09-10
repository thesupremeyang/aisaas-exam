<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { CheckCircleFilled, RobotOutlined } from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import { useAccountStore } from '@/stores'
import * as portalApi from '@/api/v2Portal'
import AgentConfigEdit from '@/components/agent/config/AgentConfigEdit.vue'
import type { AgentDefinitionVO, SkillPackageVO } from '@/types'

const router = useRouter()
const accountStore = useAccountStore()
const loading = ref(true)
const agentData = ref<AgentDefinitionVO>()
const sourceSkills = ref<SkillPackageVO[]>([])

const isPlatformAdmin = computed(
  () => accountStore.userInfo?.username?.toLowerCase() === 'admin',
)

async function loadConfig() {
  if (!isPlatformAdmin.value) {
    await router.replace('/explore')
    return
  }

  loading.value = true
  try {
    const response = await portalApi.creatorAdminConfig()
    agentData.value = response.data.data.agent
    sourceSkills.value = response.data.data.skills || []
  } finally {
    loading.value = false
  }
}

async function saveConfig(vo: AgentDefinitionVO) {
  await portalApi.updateCreatorAdminConfig(vo)
}

async function handleSaved() {
  await loadConfig()
}

onMounted(loadConfig)
</script>

<template>
  <main class="creator-admin-page">
    <header class="page-heading">
      <div class="title-mark"><RobotOutlined /></div>
      <div class="heading-copy">
        <span>平台系统能力</span>
        <h1>小图 · 创建智能体</h1>
        <p>统一维护小图的模型、提示词、Skill、MCP 与高级运行配置。</p>
      </div>
      <div class="system-status"><CheckCircleFilled /> 系统级能力已启用</div>
    </header>

    <ASpin :spinning="loading">
      <section v-if="agentData" class="config-shell">
        <AgentConfigEdit
          :agent-data="agentData"
          :tags="[]"
          :skills="sourceSkills"
          :save-handler="saveConfig"
          :show-go-visit="false"
          success-message="小图系统配置已保存并发布"
          allow-custom-prompt-without-template
          @success="handleSaved"
        />
      </section>
    </ASpin>
  </main>
</template>

<style scoped>
.creator-admin-page {
  min-height: 100%;
  padding: 28px;
  color: #1d2a3f;
  background: #f5f8fc;
}

.page-heading,
.config-shell {
  max-width: 1180px;
  margin-right: auto;
  margin-left: auto;
  border: 1px solid #dce7f5;
  background: #fff;
}

.page-heading {
  margin-bottom: 18px;
  padding: 22px 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  border-radius: 8px;
}

.title-mark {
  width: 52px;
  height: 52px;
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  color: #fff;
  font-size: 25px;
  border-radius: 8px;
  background: #2f73d9;
}

.heading-copy {
  flex: 1;
}

.heading-copy > span {
  color: #2f73d9;
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

.system-status {
  color: #2869c7;
  font-size: 13px;
  white-space: nowrap;
}

.config-shell {
  overflow: hidden;
  border-radius: 8px;
}

@media (max-width: 680px) {
  .creator-admin-page {
    padding: 14px;
  }

  .page-heading {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .system-status {
    display: none;
  }
}
</style>
