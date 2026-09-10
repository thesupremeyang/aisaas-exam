package com.hxh.apboa.assessment.service;

import com.hxh.apboa.common.entity.ExamCampaign;

import java.time.LocalDateTime;

/**
 * 考试时间窗口的统一判定规则。
 *
 * <p>结束时间采用左闭右开语义：到达截止时间的瞬间即不可继续保存或提交。</p>
 */
public final class ExamCampaignTiming {
    public enum State {
        WAITING,
        OPEN,
        ENDED
    }

    private ExamCampaignTiming() {
    }

    public static State state(ExamCampaign campaign, LocalDateTime now) {
        if (campaign == null) {
            return State.ENDED;
        }
        if (campaign.getSubmissionStart() != null && now.isBefore(campaign.getSubmissionStart())) {
            return State.WAITING;
        }
        if (campaign.getSubmissionEnd() != null && !now.isBefore(campaign.getSubmissionEnd())) {
            return State.ENDED;
        }
        return State.OPEN;
    }

    public static boolean isSubmissionOpen(ExamCampaign campaign, LocalDateTime now) {
        return state(campaign, now) == State.OPEN;
    }

    public static boolean isRegistrationOpen(ExamCampaign campaign, LocalDateTime now) {
        if (campaign == null) {
            return false;
        }
        if (campaign.getRegistrationStart() != null && now.isBefore(campaign.getRegistrationStart())) {
            return false;
        }
        return campaign.getRegistrationEnd() == null || now.isBefore(campaign.getRegistrationEnd());
    }
}
