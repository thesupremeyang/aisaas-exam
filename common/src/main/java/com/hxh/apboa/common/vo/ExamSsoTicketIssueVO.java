package com.hxh.apboa.common.vo;

import java.time.LocalDateTime;

/** The only response that contains the opaque credential. */
public record ExamSsoTicketIssueVO(String ticket, LocalDateTime expiresAt, String returnUrl) {
}
