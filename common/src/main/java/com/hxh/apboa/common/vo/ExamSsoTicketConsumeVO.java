package com.hxh.apboa.common.vo;

/** Identity context the trusted platform uses to establish its local session. */
public record ExamSsoTicketConsumeVO(Long accountId, Long tenantId, Long campaignId, String returnUrl) {
}
