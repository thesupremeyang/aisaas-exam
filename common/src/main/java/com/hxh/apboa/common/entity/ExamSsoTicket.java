package com.hxh.apboa.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.consts.TableConst;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Server-side record for a one-time examination SSO hand-off. */
@Getter
@Setter
@TableName(TableConst.EXAM_SSO_TICKET)
public class ExamSsoTicket extends BaseTenantEntity {
    private String jti;
    private String ticketHash;
    private Long accountId;
    private Long campaignId;
    private String returnUrl;
    private LocalDateTime expiresAt;
    private LocalDateTime consumedAt;
}
