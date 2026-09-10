package com.hxh.apboa.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 平台管理员维护考试批次基础信息和考试时间的请求。
 *
 * <p>结束时间由开考时间和限时计算，避免前后端分别维护两套时间。</p>
 */
@Getter
@Setter
public class ExamCampaignUpdateRequest {
    private String name;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startAt;

    private Integer durationMinutes;

    private Boolean enabled;
}
