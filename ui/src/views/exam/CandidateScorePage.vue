<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import {
  getCandidateScore,
  issueSsoTicket,
  type CandidateProgress,
  type ExamCampaign,
  type ExamCandidateScore,
} from '@/api/exam'

const props = defineProps<{
  campaign: ExamCampaign
  progress: CandidateProgress | null
}>()

const router = useRouter()
const loading = ref(false)
const openingAgentManagement = ref(false)
const score = ref<ExamCandidateScore | null>(null)

function formatScore(value?: number | null): string {
  if (value === null || value === undefined) return '—'
  return Number.isInteger(value) ? String(value) : value.toFixed(1)
}

async function loadScore() {
  if (!props.campaign.id) return
  loading.value = true
  try {
    const response = await getCandidateScore(props.campaign.id)
    score.value = response.data.data || null
  } catch {
    score.value = null
    message.error('成绩加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

async function openAgentManagement() {
  if (!props.campaign.id) return
  openingAgentManagement.value = true
  try {
    const response = await issueSsoTicket(props.campaign.id, '/#/exam/portal')
    const ticket = response.data.data?.ticket
    if (!ticket) throw new Error('no ticket')
    await router.push({ path: '/agent', query: { examTicket: ticket } })
  } catch {
    message.error('进入智能体平台失败，请稍后重试')
  } finally {
    openingAgentManagement.value = false
  }
}

onMounted(() => {
  void loadScore()
})
</script>

<template>
  <main class="candidate-score-page">
    <header class="score-heading">
      <div>
        <p class="score-eyebrow">成绩查询</p>
        <h1>{{ campaign.name || '本次考试' }}</h1>
      </div>
      <div class="score-heading-actions">
        <a-button type="primary" :loading="openingAgentManagement" @click="openAgentManagement">
          进入智能体管理
        </a-button>
        <a-button :loading="loading" @click="loadScore">
          <template #icon><ReloadOutlined /></template>
          刷新成绩
        </a-button>
      </div>
    </header>

    <a-spin :spinning="loading">
      <section class="score-summary" aria-label="考试成绩">
        <article class="score-card score-card-total">
          <span class="score-card-label">总成绩</span>
          <div class="score-value-row">
            <strong>{{ formatScore(score?.totalScore) }}</strong>
            <small>/ {{ score?.totalScoreMax ?? 100 }} 分</small>
          </div>
          <span class="score-card-hint">最终成绩</span>
        </article>
        <article class="score-card">
          <span class="score-card-label">理论得分</span>
          <div class="score-value-row">
            <strong>{{ formatScore(score?.theoryScore) }}</strong>
            <small>/ {{ score?.theoryScoreMax ?? 100 }} 分</small>
          </div>
          <span class="score-card-hint">计入总成绩 20%</span>
        </article>
        <article class="score-card">
          <span class="score-card-label">实操得分</span>
          <div class="score-value-row">
            <strong>{{ formatScore(score?.practicalScore) }}</strong>
            <small>/ {{ score?.practicalScoreMax ?? 100 }} 分</small>
          </div>
          <span class="score-card-hint">计入总成绩 80%</span>
        </article>
      </section>
    </a-spin>

    <p v-if="score?.totalScore === null || score?.totalScore === undefined" class="score-pending">
      当前成绩尚未完整录入，请稍后再查看。
    </p>
    <p class="score-formula">总成绩 = 理论得分 × 20% + 实操得分 × 80%</p>

    <section class="submission-list-section">
      <div class="section-title-row">
        <div>
          <p class="score-eyebrow">题目配置</p>
          <h2>查看各题关联的智能体</h2>
        </div>
      </div>

      <a-empty
        v-if="!loading && !score?.questions?.length"
        description="暂无已关联的智能体"
      />
      <a-list v-else :data-source="score?.questions || []" :split="false" class="submission-list">
        <template #renderItem="{ item }">
          <a-list-item>
            <div class="submission-item-main">
              <span class="question-index">{{ item.questionNo }}</span>
              <div class="submission-item-copy">
                <strong>{{ item.questionTitle || '未命名试题' }}</strong>
                <span>
                  {{ item.agentName ? `关联智能体：${item.agentName}` : '该试题未关联智能体' }}
                  <template v-if="item.questionScore"> · 本题满分 {{ item.questionScore }} 分</template>
                </span>
              </div>
            </div>
            <a-button
              type="primary"
              ghost
              :loading="openingAgentManagement"
              @click="openAgentManagement"
            >
              进入智能体管理
            </a-button>
          </a-list-item>
        </template>
      </a-list>
    </section>
  </main>
</template>

<style scoped lang="scss">
.candidate-score-page {
  max-width: 1180px;
  margin: 0 auto;
  padding: 34px 20px 64px;
  color: #17233f;
}

.score-heading,
.section-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}

.score-heading {
  margin-bottom: 26px;
}

.score-heading-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.score-eyebrow {
  margin: 0 0 8px;
  color: #5f6f8d;
  font-size: 12px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.score-heading h1,
.section-title-row h2 {
  margin: 0;
  color: #14213d;
}

.score-heading h1 {
  font-size: 28px;
}

.score-summary {
  display: grid;
  grid-template-columns: 1.25fr 1fr 1fr;
  gap: 16px;
}

.score-card {
  min-height: 150px;
  padding: 24px 26px;
  border: 1px solid #dbe6f6;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 10px 26px rgba(38, 69, 125, 0.08);
}

.score-card-total {
  border-color: #bed0ff;
  background: linear-gradient(135deg, #eef3ff, #fff);
}

.score-card-label,
.score-card-hint {
  display: block;
}

.score-card-label {
  color: #5b6b88;
  font-size: 14px;
}

.score-value-row {
  display: flex;
  align-items: baseline;
  gap: 7px;
  margin-top: 16px;
}

.score-value-row strong {
  color: #1f54d8;
  font-size: 38px;
  line-height: 1;
}

.score-card:not(.score-card-total) .score-value-row strong {
  color: #17233f;
  font-size: 30px;
}

.score-value-row small {
  color: #7887a2;
}

.score-card-hint {
  margin-top: 12px;
  color: #8996aa;
  font-size: 12px;
}

.score-formula,
.score-pending {
  margin: 16px 0 0;
  color: #667594;
  font-size: 13px;
}

.score-pending {
  color: #b26b00;
}

.submission-list-section {
  margin-top: 42px;
}

.section-title-row {
  align-items: center;
  margin-bottom: 16px;
}

.section-title-row h2 {
  font-size: 22px;
}

.submission-list {
  overflow: hidden;
  border: 1px solid #dbe6f6;
  border-radius: 14px;
  background: #fff;
}

.submission-list :deep(.ant-list-item) {
  padding: 18px 22px;
}

.submission-item-main {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 14px;
}

.question-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 34px;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  color: #2859d8;
  background: #edf2ff;
  font-weight: 700;
}

.submission-item-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 6px;
}

.submission-item-copy strong {
  overflow: hidden;
  color: #203252;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.submission-item-copy span {
  color: #8491a7;
  font-size: 12px;
}

@media (max-width: 760px) {
  .score-heading,
  .section-title-row {
    flex-direction: column;
  }

  .score-summary {
    grid-template-columns: 1fr;
  }

  .submission-list :deep(.ant-list-item) {
    align-items: flex-start;
    flex-direction: column;
    gap: 14px;
  }
}
</style>
