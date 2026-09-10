package com.hxh.apboa.common.vo;

import com.hxh.apboa.common.config.SerializableEnable;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 聊天会话 VO
 *
 * @author huxuehao
 */
@Getter
@Setter
public class ChatSessionVO implements SerializableEnable {
    private Long id;
    private Long userId;
    private Long agentId;
    private Long currentMessageId;
    /**
     * 消息所在归档表名
     */
    private String messageTable;
    private String title;
    private Boolean isPinned;
    private LocalDateTime pinTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
