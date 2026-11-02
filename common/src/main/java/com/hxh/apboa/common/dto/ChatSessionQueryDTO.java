package com.hxh.apboa.common.dto;

import com.hxh.apboa.common.config.SerializableEnable;
import com.hxh.apboa.common.mp.support.PageParams;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * 会话列表查询 DTO（按用户、智能体等筛选，支持分页）
 *
 * @author huxuehao
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class ChatSessionQueryDTO extends PageParams implements SerializableEnable {
    private Long userId;
    /**
     * 智能体 key：数字 id 或 agentCode，服务端解析后再查询
     */
    private String agentId;
    private Boolean isPinned;
}
