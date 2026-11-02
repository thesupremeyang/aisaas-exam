package com.hxh.apboa.agent.service;

import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.entity.SkillPackage;
import com.hxh.apboa.common.entity.ToolConfig;
import com.hxh.apboa.common.vo.AgentDefinitionVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 智能体定义Service
 *
 * @author huxuehao
 */
public interface AgentDefinitionService extends IService<AgentDefinition> {
    /**
     * 将外部传入的智能体 key 解析为内部数字 id。
     * 兼容两种写法：数字主键 id，或 agentCode（如 exam-candidate-xxx-agent001）。
     */
    Long resolveAgentId(String agentKey);

    AgentDefinitionVO agentDefinitionDetail(Long id);
    Boolean saveAgentDefinition(AgentDefinitionVO agentDefinition);
    Boolean updateAgentDefinition(AgentDefinitionVO agentDefinition);
    Boolean deleteAgentDefinition(List<Long> ids);
    List<Object> usedWithAgent(List<Long> ids);
    /**
     * 获取所有Tag
     *
     * @return Tag列表
     */
    List<String> listTags();
    List<String> allowFileType(Long id);
    List<ToolConfig> getEnabledToolsOfAgent(Long agentId);
    List<SkillPackage> getEnabledSkillsOfAgent(Long agentId);
}
