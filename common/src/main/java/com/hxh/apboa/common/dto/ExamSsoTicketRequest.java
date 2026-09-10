package com.hxh.apboa.common.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExamSsoTicketRequest {
    private Long campaignId;
    private String returnUrl;
}
