package com.hxh.apboa.common.dto;

import com.hxh.apboa.common.config.SerializableEnable;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建会话 DTO
 *
 * @author huxuehao
 */
@Getter
@Setter
public class ChatSessionCreateDTO implements SerializableEnable {
    /**
     * 智能体 key：数字 id 或 agentCode，服务端解析后落库为数字 id
     */
    private String agentId;
    /**
     * 会话标题，可选
     */
    private String title;
    /**
     * 是否初始化工作区
     */
    private Boolean initWorkspace;
}
