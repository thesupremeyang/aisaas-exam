package com.hxh.apboa.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 平台管理员配置考试批次时间窗口的请求。
 *
 * <p>结束时间由开考时间和考试时长计算，避免前后端分别维护两套时间。</p>
 */
@Getter
@Setter
public class ExamCampaignScheduleRequest {
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startAt;
    private Integer durationMinutes;
}
