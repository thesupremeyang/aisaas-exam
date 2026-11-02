<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { useExamStore } from '@/stores'
import type { AssessmentReview } from '@/api/exam'
import { isScoreWithinRange } from './review'
import { currentExamRubric } from './rubric'

const props = defineProps<{ submissionId: string }>()
const examStore = useExamStore()

const rubric = currentExamRubric()
const scores = reactive<Record<string, number>>(
  Object.fromEntries(rubric.map((item) => [item.key, 0])),
)
const comment = ref('')
const checklist = reactive({
  materialChecked: false,
  originalityChecked: false,
  riskChecked: false,
})
const published = computed(() => examStore.review?.status === 'PUBLISHED')
const totalScore = computed(() =>
  Object.values(scores).reduce((total, score) => total + (Number(score) || 0), 0),
)
const rubricTotal = computed(() => rubric.reduce((total, item) => total + item.maximum, 0))

function hydrate(review: AssessmentReview | null) {
  if (!review) return
  try {
    const savedScores = JSON.parse(review.scoresJson || '{}') as Record<string, number>
    rubric.forEach((item) => {
      scores[item.key] = Number(savedScores[item.key]) || 0
    })
  } catch {
    rubric.forEach((item) => {
      scores[item.key] = 0
    })
  }
  comment.value = review.comment || ''
  try {
    Object.assign(checklist, JSON.parse(review.checklistJson || '{}'))
  } catch {
    /* keep defaults */
  }
}

function payload(): AssessmentReview {
  return {
    id: examStore.review?.id,
    submissionId: props.submissionId,
    scoresJson: JSON.stringify(scores),
    totalScore: totalScore.value,
    comment: comment.value.trim(),
    checklistJson: JSON.stringify(checklist),
  }
}

function validate() {
  if (comment.value.trim().length < 10) {
    message.warning('复核意见至少需要 10 个字符')
    return false
  }
  if (rubric.some((item) => !isScoreWithinRange(scores[item.key], item.maximum))) {
    message.warning('请检查各评分项是否在允许范围内')
    return false
  }
  return true
}

async function load() {
  try {
    hydrate(await examStore.loadReview(props.submissionId))
  } catch {
    message.warning('无法加载复核信息，请稍后重试')
  }
}

async function save() {
  if (!validate() || published.value) return
  try {
    await examStore.saveReview(props.submissionId, payload())
    message.success('复核草稿已保存')
  } catch {
    message.warning('保存复核草稿失败')
  }
}

function publish() {
  if (!validate() || published.value) return
  Modal.confirm({
    title: '确认发布最终成绩？',
    content: `将发布 ${totalScore.value} 分的最终成绩；发布后不能再修改本次复核。`,
    okText: '确认发布',
    onOk: async () => {
      try {
        await examStore.saveReview(props.submissionId, payload())
        await examStore.publishReview(props.submissionId)
        message.success('最终成绩已发布')
      } catch {
        message.warning('成绩发布失败，请稍后重试')
      }
    },
  })
}

watch(() => props.submissionId, load)
onMounted(load)
</script>

<template>
  <main class="review-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">单评委复核</p>
        <h1>复核并发布作品成绩</h1>
        <p>评委独立填写分项评分与复核意见，最终成绩发布后不可修改。</p>
      </div>
      <a-tag :color="published ? 'success' : 'blue'" class="status-tag">{{
        published ? '成绩已发布' : '待发布'
      }}</a-tag>
    </header>

    <section class="review-grid">
      <div>
        <a-card :loading="examStore.loading" class="review-card">
          <div class="card-heading">
            <div>
              <p class="eyebrow">复核评分</p>
              <h2>按评分量表给出最终分数</h2>
            </div>
            <div class="total-score">
              <span>复核总分</span><strong>{{ totalScore }}</strong
              ><em>/ {{ rubricTotal }}</em>
            </div>
          </div>
          <div v-for="item in rubric" :key="item.key" class="score-row">
            <div>
              <b>{{ item.label }}</b
              ><span>满分 {{ item.maximum }} 分</span>
            </div>
            <a-input-number
              v-model:value="scores[item.key]"
              :min="0"
              :max="item.maximum"
              :precision="0"
              :disabled="published"
            />
          </div>
        </a-card>

        <a-card class="review-card opinion-card">
          <h2>复核意见</h2>
          <a-textarea
            v-model:value="comment"
            :rows="5"
            :maxlength="1000"
            :disabled="published"
            placeholder="请说明评分依据、待改进项或需要关注的风险（至少 10 个字符）"
          />
          <a-space direction="vertical" class="checklist">
            <a-checkbox v-model:checked="checklist.materialChecked" :disabled="published"
              >已核验考生提交材料与作品说明</a-checkbox
            >
            <a-checkbox v-model:checked="checklist.originalityChecked" :disabled="published"
              >已核验原创/授权声明</a-checkbox
            >
            <a-checkbox v-model:checked="checklist.riskChecked" :disabled="published"
              >已记录需关注的风险或改进建议</a-checkbox
            >
          </a-space>
          <div v-if="!published" class="actions">
            <a-button :loading="examStore.saving" @click="save">保存复核草稿</a-button
            ><a-button type="primary" :loading="examStore.publishing" @click="publish"
              >确认并发布成绩</a-button
            >
          </div>
        </a-card>
      </div>

      <aside class="notice-card">
        <p class="eyebrow">发布提示</p>
        <h3>发布前请确认</h3>
        <ol>
          <li>总分会自动按五个分项累加。</li>
          <li>复核意见至少填写 10 个字符。</li>
          <li>发布后作品状态变为“成绩已发布”。</li>
        </ol>
        <a-alert
          type="info"
          show-icon
          message="实际总分上限和成绩等级将按考试批次量表在服务端校验。"
        />
      </aside>
    </section>
  </main>
</template>

<style scoped lang="scss">
.review-page {
  max-width: 1180px;
  margin: 0 auto;
  padding: 32px 24px 56px;
  color: #14213d;
}
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 24px;
}
.page-header h1 {
  margin: 4px 0 8px;
  font-size: 30px;
}
.page-header p {
  margin: 0;
  color: #62728b;
}
.eyebrow {
  margin: 0;
  color: #1677ff;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}
.status-tag {
  margin-top: 10px;
  padding: 4px 10px;
}
.review-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 270px;
  gap: 20px;
}
.review-card {
  margin-bottom: 20px;
}
.card-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}
.card-heading h2,
.opinion-card h2 {
  margin: 4px 0 0;
  font-size: 19px;
}
.total-score {
  text-align: right;
}
.total-score span {
  display: block;
  color: #7c8da6;
  font-size: 12px;
}
.total-score strong {
  font-size: 36px;
  color: #0e5fca;
}
.total-score em {
  margin-left: 5px;
  color: #7c8da6;
  font-style: normal;
}
.score-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 15px 0;
  border-top: 1px solid #edf1f7;
}
.score-row b,
.score-row span {
  display: block;
}
.score-row span {
  margin-top: 4px;
  color: #7c8da6;
  font-size: 12px;
}
.score-row :deep(.ant-input-number) {
  width: 110px;
}
.checklist {
  margin-top: 16px;
}
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
}
.notice-card {
  height: fit-content;
  padding: 22px;
  border: 1px solid #dcecff;
  border-radius: 12px;
  background: #f7fbff;
}
.notice-card h3 {
  margin: 8px 0 12px;
}
.notice-card ol {
  margin: 0 0 18px;
  padding-left: 20px;
  color: #62728b;
  line-height: 2;
  font-size: 13px;
}
.notice-card :deep(.ant-alert-message) {
  font-size: 12px;
  line-height: 1.6;
}
@media (max-width: 850px) {
  .review-grid {
    grid-template-columns: 1fr;
  }
  .notice-card {
    display: none;
  }
}
@media (max-width: 640px) {
  .review-page {
    padding: 20px 14px;
  }
  .page-header {
    display: block;
  }
  .card-heading {
    align-items: flex-start;
    gap: 10px;
    flex-direction: column;
  }
  .total-score {
    text-align: left;
  }
  .actions {
    align-items: stretch;
    flex-direction: column;
  }
  .actions button {
    width: 100%;
  }
}
</style>
