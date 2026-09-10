package com.hxh.apboa.assessment.service;

import com.hxh.apboa.common.entity.ExamSsoTicket;

import java.time.LocalDateTime;

/** State guard shared by ticket consumption and its persistence transition. */
public final class ExamSsoTicketState {
    private ExamSsoTicketState() {
    }

    public static void assertConsumable(ExamSsoTicket ticket, LocalDateTime now) {
        if (ticket == null || ticket.getConsumedAt() != null) {
            throw new IllegalStateException("SSO 凭证已被使用或不存在");
        }
        if (ticket.getExpiresAt() == null || !ticket.getExpiresAt().isAfter(now)) {
            throw new IllegalStateException("SSO 凭证已过期");
        }
    }
}
