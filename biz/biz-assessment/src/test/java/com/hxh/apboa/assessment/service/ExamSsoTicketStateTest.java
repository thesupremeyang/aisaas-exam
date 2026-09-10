package com.hxh.apboa.assessment.service;

import com.hxh.apboa.common.entity.ExamSsoTicket;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExamSsoTicketStateTest {

    @Test
    void permitsOnlyUnconsumedAndUnexpiredTicket() {
        var now = LocalDateTime.of(2026, 8, 1, 10, 0);
        var active = new ExamSsoTicket();
        active.setExpiresAt(now.plusMinutes(5));

        assertDoesNotThrow(() -> ExamSsoTicketState.assertConsumable(active, now));

        active.setConsumedAt(now.plusMinutes(1));
        assertThrows(IllegalStateException.class, () -> ExamSsoTicketState.assertConsumable(active, now.plusMinutes(1)));

        active.setConsumedAt(null);
        active.setExpiresAt(now.minusSeconds(1));
        assertThrows(IllegalStateException.class, () -> ExamSsoTicketState.assertConsumable(active, now));
    }
}
