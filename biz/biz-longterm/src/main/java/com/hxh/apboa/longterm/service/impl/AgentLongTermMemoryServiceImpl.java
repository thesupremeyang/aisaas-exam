package com.hxh.apboa.longterm.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hxh.apboa.common.entity.AgentLongTermMemory;
import com.hxh.apboa.longterm.mapper.AgentLongTermMemoryMapper;
import com.hxh.apboa.longterm.service.AgentLongTermMemoryService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 描述：AgentLongTermMemoryServiceImpl
 *
 * @author huxuehao
 **/
@Service
public class AgentLongTermMemoryServiceImpl extends ServiceImpl<AgentLongTermMemoryMapper, AgentLongTermMemory> implements AgentLongTermMemoryService {
    @Override
    public List<Long> getAgentIds(List<Long> configId) {
        return lambdaQuery()
                .in(AgentLongTermMemory::getLongTermMemoryConfigId, configId)
                .list()
                .stream()
                .map(AgentLongTermMemory::getAgentDefinitionId)
                .distinct()
                .toList();
    }

    @Override
    public Long getConfigIdByAgentId(Long agentId) {
        return lambdaQuery()
                .eq(AgentLongTermMemory::getAgentDefinitionId, agentId)
                .oneOpt()
                .map(AgentLongTermMemory::getLongTermMemoryConfigId)
                .orElse(null);
    }

    @Override
    public Boolean insertAgentLongTermMemory(Long agentDefinitionId, Long configId) {
        if (configId == null) {
            return false;
        }
        save(new AgentLongTermMemory(null, null, agentDefinitionId, configId));

        return true;
    }

    @Override
    public Boolean deleteAgentLongTermMemory(List<Long> agentIds) {
        if (agentIds == null || agentIds.isEmpty()) {
            return true;
        }
        return lambdaUpdate().in(AgentLongTermMemory::getAgentDefinitionId, agentIds).remove();
    }

    @Override
    public Boolean saveAgentLongTermMemory(Long agentDefinitionId, Long configId) {
        deleteAgentLongTermMemory(List.of(agentDefinitionId));
        insertAgentLongTermMemory(agentDefinitionId, configId);

        return Boolean.TRUE;
    }
}
