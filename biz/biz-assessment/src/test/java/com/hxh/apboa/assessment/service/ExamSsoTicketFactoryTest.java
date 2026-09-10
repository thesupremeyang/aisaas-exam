package com.hxh.apboa.assessment.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ExamSsoTicketFactoryTest {

    @Test
    void issuesFiveMinuteTicketWithoutPersistingRawCredential() {
        var issuedAt = LocalDateTime.of(2026, 8, 1, 10, 0);
        var issued = new ExamSsoTicketFactory().issue(10L, 20L, 30L, "/exam/candidate", issuedAt);

        assertEquals(issuedAt.plusMinutes(5), issued.ticket().getExpiresAt());
        assertEquals(20L, issued.ticket().getAccountId());
        assertEquals(30L, issued.ticket().getCampaignId());
        assertFalse(issued.ticket().getTicketHash().contains(issued.rawTicket()));
    }
}
