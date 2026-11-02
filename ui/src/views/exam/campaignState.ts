/**
 * 考试批次状态判定（共享工具）
 *
 * 供场次管理、成绩与发布等 admin 页面复用，避免状态机逻辑在多处漂移。
 * 状态口径与 ExamCampaignAdminPage 保持一致。
 *
 * @author workbuddy
 */

import dayjs, { type Dayjs } from 'dayjs'
import type { ExamCampaign } from '@/api/exam'

export type CampaignState =
  | 'DISABLED'
  | 'DRAFT'
  | 'UNSCHEDULED'
  | 'WAITING'
  | 'OPEN'
  | 'ENDED'
  | 'CLOSED'

export function parseExamTime(value?: string): Dayjs | null {
  if (!value) return null
  const normalized = value.includes(' ') && !value.includes('T') ? value.replace(' ', 'T') : value
  const parsed = dayjs(normalized)
  return parsed.isValid() ? parsed : null
}

export function isCampaignEnabled(campaign: ExamCampaign): boolean {
  return campaign.enabled !== false
}

export function isScoreQueryEnabled(campaign: ExamCampaign): boolean {
  if (typeof campaign.scoreQueryEnabled === 'boolean') {
    return campaign.scoreQueryEnabled
  }
  try {
    const parsed = campaign.configJson ? JSON.parse(campaign.configJson) : null
    return parsed?.scoreQueryEnabled === true
  } catch {
    return false
  }
}

export function campaignState(campaign: ExamCampaign, nowMs: number): CampaignState {
  if (!isCampaignEnabled(campaign)) return 'DISABLED'
  if (campaign.status === 'DRAFT') return 'DRAFT'
  if (campaign.status === 'CLOSED') return 'CLOSED'

  const start = parseExamTime(campaign.submissionStart)
  if (!start) return 'UNSCHEDULED'
  if (nowMs < start.valueOf()) return 'WAITING'

  const end = parseExamTime(campaign.submissionEnd)
  if (end && nowMs >= end.valueOf()) return 'ENDED'
  return 'OPEN'
}

export function campaignStateLabel(state: CampaignState): string {
  return {
    DISABLED: '已停用',
    DRAFT: '草稿',
    UNSCHEDULED: '待设置',
    WAITING: '未开始',
    OPEN: '进行中',
    ENDED: '已结束',
    CLOSED: '已关闭',
  }[state]
}

export function campaignStateColor(state: CampaignState): string {
  return {
    DISABLED: 'default',
    DRAFT: 'gold',
    UNSCHEDULED: 'orange',
    WAITING: 'blue',
    OPEN: 'green',
    ENDED: 'default',
    CLOSED: 'default',
  }[state]
}

/** 考试已结束且尚未开启查分：即"待发布成绩" */
export function isPendingScorePublish(campaign: ExamCampaign, nowMs: number): boolean {
  return campaignState(campaign, nowMs) === 'ENDED' && !isScoreQueryEnabled(campaign)
}
