package com.hxh.apboa.assessment.service;

import com.hxh.apboa.common.entity.ExamCampaign;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ExamCampaignTimingTest {
    private final LocalDateTime start = LocalDateTime.of(2026, 8, 27, 9, 0);
    private final LocalDateTime end = start.plusHours(2);

    @Test
    void usesWaitingOpenAndEndedStatesWithExclusiveEnd() {
        var campaign = campaign();

        assertEquals(ExamCampaignTiming.State.WAITING,
                ExamCampaignTiming.state(campaign, start.minusSeconds(1)));
        assertEquals(ExamCampaignTiming.State.OPEN,
                ExamCampaignTiming.state(campaign, start));
        assertTrue(ExamCampaignTiming.isSubmissionOpen(campaign, end.minusSeconds(1)));
        assertEquals(ExamCampaignTiming.State.ENDED,
                ExamCampaignTiming.state(campaign, end));
        assertFalse(ExamCampaignTiming.isSubmissionOpen(campaign, end));
    }

    @Test
    void registrationWindowIsIndependentFromExamWindow() {
        var campaign = campaign();
        campaign.setRegistrationStart(start.minusDays(1));
        campaign.setRegistrationEnd(start.minusHours(1));

        assertTrue(ExamCampaignTiming.isRegistrationOpen(campaign, start.minusHours(2)));
        assertFalse(ExamCampaignTiming.isRegistrationOpen(campaign, start.minusHours(1)));
    }

    private ExamCampaign campaign() {
        var campaign = new ExamCampaign();
        campaign.setSubmissionStart(start);
        campaign.setSubmissionEnd(end);
        return campaign;
    }
}
