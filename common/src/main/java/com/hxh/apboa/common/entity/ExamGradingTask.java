package com.hxh.apboa.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.consts.TableConst;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AI 阅卷任务（基于考生智能体真实配置的评分，区别于旧 AI 智评 assessment_task）。
 * 评分对象是 agent_definition 提示词 + 知识库文档切片 + Skill 文件，不含截图/视频等运行证据。
 */
@Getter
@Setter
@TableName(TableConst.EXAM_GRADING_TASK)
public class ExamGradingTask extends BaseTenantEntity {
    private Long campaignId;
    private Long submissionId;
    /** QUEUED / RUNNING / SUCCEEDED / FAILED */
    private String status;
    private Integer progress;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
