package com.hxh.apboa.longterm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hxh.apboa.common.entity.AgentLongTermMemory;

import java.util.List;

/**
 * 描述：AgentLongTermMemoryService
 *
 * @author huxuehao
 **/
public interface AgentLongTermMemoryService extends IService<AgentLongTermMemory> {
    List<Long> getAgentIds(List<Long> configId);
    Long getConfigIdByAgentId(Long agentId);
    Boolean insertAgentLongTermMemory(Long agentDefinitionId, Long configId);
    Boolean deleteAgentLongTermMemory(List<Long> agentIds);
    Boolean saveAgentLongTermMemory(Long agentDefinitionId, Long configId);
}
