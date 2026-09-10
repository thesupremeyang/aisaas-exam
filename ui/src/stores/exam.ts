import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as examApi from '@/api/exam'
import type { AssessmentReport, AssessmentReview, AssessmentTask, ExamSubmission } from '@/api/exam'

export const useExamStore = defineStore('exam', () => {
  const submission = ref<ExamSubmission | null>(null)
  const task = ref<AssessmentTask | null>(null)
  const report = ref<AssessmentReport | null>(null)
  const review = ref<AssessmentReview | null>(null)
  const loading = ref(false)
  const saving = ref(false)
  const submitting = ref(false)
  const publishing = ref(false)
  const error = ref<string>('')
  const isAssessmentFinished = computed(
    () => task.value?.status === 'SUCCEEDED' || task.value?.status === 'FAILED',
  )

  async function loadSubmission(submissionId: string) {
    loading.value = true
    error.value = ''
    try {
      const response = await examApi.getCandidateSubmission(submissionId)
      submission.value = response.data.data
      return submission.value
    } finally {
      loading.value = false
    }
  }

  async function saveDraft(submissionId: string, payload: ExamSubmission) {
    saving.value = true
    try {
      await examApi.saveCandidateDraft(submissionId, payload)
      if (submission.value?.id === submissionId) {
        submission.value = {
          ...submission.value,
          agentId: payload.agentId,
          formJson: payload.formJson,
          currentStep: payload.currentStep,
          version: (submission.value.version ?? 0) + 1,
        }
      }
    } finally {
      saving.value = false
    }
  }

  async function submitWork(submissionId: string) {
    submitting.value = true
    try {
      const response = await examApi.submitCandidateWork(submissionId)
      submission.value = response.data.data
      return submission.value
    } finally {
      submitting.value = false
    }
  }

  async function retryAssessment(submissionId: string) {
    const response = await examApi.retryCandidateAssessment(submissionId)
    submission.value = response.data.data
    task.value = null
    report.value = null
    return submission.value
  }

  async function loadTask(submissionId: string) {
    const response = await examApi.getAssessmentTask(submissionId)
    task.value = response.data.data
    return task.value
  }

  async function loadReport(submissionId: string) {
    const response = await examApi.getAssessmentReport(submissionId)
    report.value = response.data.data
    return report.value
  }

  async function loadReview(submissionId: string) {
    const response = await examApi.getReviewerAssessment(submissionId)
    review.value = response.data.data
    return review.value
  }

  async function saveReview(submissionId: string, payload: AssessmentReview) {
    saving.value = true
    try {
      await examApi.saveReviewerAssessment(submissionId, payload)
      review.value = { ...review.value, ...payload, submissionId, status: 'DRAFT' }
    } finally {
      saving.value = false
    }
  }

  async function publishReview(submissionId: string) {
    publishing.value = true
    try {
      const response = await examApi.publishReviewerAssessment(submissionId)
      review.value = response.data.data
      return review.value
    } finally {
      publishing.value = false
    }
  }

  function clear() {
    submission.value = null
    task.value = null
    report.value = null
    review.value = null
    error.value = ''
  }

  return {
    submission,
    task,
    report,
    review,
    loading,
    saving,
    submitting,
    publishing,
    error,
    isAssessmentFinished,
    loadSubmission,
    saveDraft,
    submitWork,
    retryAssessment,
    loadTask,
    loadReport,
    loadReview,
    saveReview,
    publishReview,
    clear,
  }
})
