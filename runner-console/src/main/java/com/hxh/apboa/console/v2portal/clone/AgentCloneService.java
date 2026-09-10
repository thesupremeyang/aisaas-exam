package com.hxh.apboa.console.v2portal.clone;

import com.hxh.apboa.agent.service.AgentDefinitionService;
import com.hxh.apboa.console.v2portal.clone.AgentCloneRequestDTO;
import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.enums.AgentType;
import com.hxh.apboa.common.exception.BusinessException;
import com.hxh.apboa.common.util.UserUtils;
import com.hxh.apboa.common.vo.AgentCloneResponseVO;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hxh.apboa.common.vo.AgentDefinitionVO;
import com.hxh.apboa.console.v2portal.clone.mapper.AgentCrossTenantReadMapper;
import com.hxh.apboa.console.v2portal.clone.mapper.AgentSubItemCloneMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 广场克隆 agent 业务逻辑
 * <p>
 * 业务流程：
 * <ol>
 *   <li>跨租户读源 agent_definition（@InterceptorIgnore）</li>
 *   <li>构造新 agent_definition：tenantId=caller, name=name+" (副本)",
 *       agentCode=srcAgentCode+"-copy-"+UUID8, enabled=false</li>
 *   <li>重置所有跨租户不可访问的资源引用（model_config_id / knowledge / mcp /
 *       skill / system_prompt_template / sensitive_word_config_id 等）</li>
 *   <li>复用现有 AgentDefinitionService.saveAgentDefinition 插入新行</li>
 *   <li>复制纯关系子表（agent_hooks / agent_tools / agent_sub_agents）</li>
 *   <li>写 agent_clone_record 审计</li>
 * </ol>
 *
 * @author agent-clone-feature
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentCloneService {

    private final AgentCrossTenantReadMapper crossReader;
    private final AgentSubItemCloneMapper cloneMapper;
    private final AgentDefinitionService agentDefinitionService;
    private final AgentCloneRecordMapper recordMapper;
    private final AgentCloneResourceHelper resourceHelper;

    /**
     * 克隆源 agent 到 caller 所在租户。
     *
     * @param req          请求 DTO
     * @param callerTenantId caller 租户 id
     * @return 新 agent 关键信息
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentCloneResponseVO cloneAgent(AgentCloneRequestDTO req, Long callerTenantId) {
        if (callerTenantId == null) {
            throw new BusinessException(AgentCloneConstants.ERR_NO_CALLER_TENANT);
        }

        // 1. 跨租户读源 agent（绕过 tenant 过滤）
        AgentDefinition source = crossReader.selectByIdIgnoreTenant(req.getSourceAgentId());
        if (source == null) {
            throw new BusinessException(AgentCloneConstants.ERR_SOURCE_NOT_FOUND);
        }
        if (Boolean.FALSE.equals(source.getEnabled())) {
            throw new BusinessException(AgentCloneConstants.ERR_SOURCE_DISABLED);
        }

        // 2. 构造新 agent 的 VO
        AgentDefinitionVO newVo = buildClonedVo(source, callerTenantId);

        // 2.1 在目标租户创建独立提示词模板，并绑定到新智能体
        resourceHelper.copyAndBindPromptTemplate(source, newVo, callerTenantId);

        // 3. 复用现有 service 插入新行（子表全部传空，依赖 service 的 saveSubItems）
        Boolean ok = agentDefinitionService.saveAgentDefinition(newVo);
        if (!Boolean.TRUE.equals(ok) || newVo.getId() == null) {
            throw new BusinessException("克隆失败：保存新智能体失败");
        }

        // 4. 复制纯关系子表（仅复制与租户无关的关系数据）
        // 表使用 ASSIGN_ID（雪花算法），需在 Java 层生成 ID 后逐行 INSERT
        try {
            int hooks = 0, tools = 0, subs = 0;
            for (Long hookConfigId : cloneMapper.selectHookConfigIdsByAgentId(source.getId())) {
                cloneMapper.insertHookRow(IdWorker.getId(), callerTenantId, newVo.getId(), hookConfigId);
                hooks++;
            }
            for (Long toolId : cloneMapper.selectToolIdsByAgentId(source.getId())) {
                cloneMapper.insertToolRow(IdWorker.getId(), callerTenantId, newVo.getId(), toolId);
                tools++;
            }
            for (Long subAgentId : cloneMapper.selectSubAgentIdsByAgentId(source.getId())) {
                cloneMapper.insertSubAgentRow(IdWorker.getId(), callerTenantId, newVo.getId(), subAgentId);
                subs++;
            }
            log.info("[AgentClone] sub-item clone: hooks={}, tools={}, subAgents={}, src={}, new={}",
                    hooks, tools, subs, source.getId(), newVo.getId());
        } catch (Exception e) {
            // 子表复制失败不回滚（agent_definition 已经写入），仅记录日志
            // 业务影响：克隆的 agent 缺少 hook/tool/sub_agent 关联
            log.error("[AgentClone] sub-item clone failed, src={}, new={}",
                    source.getId(), newVo.getId(), e);
        }

        // 4.1 深度复制技能包到目标租户（包含主表、文件、内置工具关联）
        try {
            List<Long> newSkillIds = resourceHelper.copySkills(source.getId(), newVo.getId(), callerTenantId);
            log.info("[AgentClone] skills copied: count={}, src={}, new={}",
                    newSkillIds.size(), source.getId(), newVo.getId());
        } catch (Exception e) {
            log.error("[AgentClone] skill copy failed, src={}, new={}",
                    source.getId(), newVo.getId(), e);
        }

        // 4.2 深度复制Studio配置
        try {
            Long newStudioId = resourceHelper.copyStudioConfig(source.getId(), newVo.getId(), callerTenantId);
            if (newStudioId != null) {
                log.info("[AgentClone] studio config copied: srcAgent={}, newConfig={}",
                        source.getId(), newStudioId);
            }
        } catch (Exception e) {
            log.error("[AgentClone] studio config copy failed, src={}, new={}",
                    source.getId(), newVo.getId(), e);
        }

        // 4.3 深度复制代码执行环境配置
        try {
            Long newCodeExecId = resourceHelper.copyCodeExecutionConfig(source.getId(), newVo.getId(), callerTenantId);
            if (newCodeExecId != null) {
                log.info("[AgentClone] code execution config copied: srcAgent={}, newConfig={}",
                        source.getId(), newCodeExecId);
            }
        } catch (Exception e) {
            log.error("[AgentClone] code execution config copy failed, src={}, new={}",
                    source.getId(), newVo.getId(), e);
        }

        // 5. 写审计
        AgentCloneRecord record = new AgentCloneRecord();
        record.setSourceAgentId(source.getId());
        record.setSourceTenantId(source.getTenantId());
        record.setNewAgentId(newVo.getId());
        record.setNewTenantId(callerTenantId);
        record.setClonedBy(UserUtils.getId());
        record.setSourceAgentCode(source.getAgentCode());
        record.setNewAgentCode(newVo.getAgentCode());
        recordMapper.insert(record);

        // 6. 构造响应
        AgentCloneResponseVO resp = new AgentCloneResponseVO();
        resp.setId(newVo.getId());
        resp.setName(newVo.getName());
        resp.setAgentCode(newVo.getAgentCode());
        resp.setTenantId(callerTenantId);
        resp.setEnabled(newVo.getEnabled());
        resp.setSourceAgentId(source.getId());
        return resp;
    }

    /**
     * 构造克隆后的新 agent VO
     * <p>
     * 关键处理：
     * <ul>
     *   <li>id/createdAt/updatedAt/createdBy/updatedBy：置 null 让 MyBatis-Plus 重填</li>
     *   <li>tenantId：caller 租户</li>
     *   <li>name：源 name + " (副本)"</li>
     *   <li>agentCode：源 code + "-copy-" + UUID8</li>
     *   <li>enabled：false（默认禁用，让用户进编辑页改）</li>
     *   <li>model_config_id / system_prompt_template_id / sensitive_word_config_id：
     *       全部置 null（避免跨租户越权引用）</li>
     *   <li>提示词：由resourceHelper在目标租户创建独立模板，并绑定到新智能体</li>
     *   <li>knowledgeBase / mcp / skill / tool / subAgent / hook 子项：传空集合（skill在保存agent后单独深度复制）</li>
     *   <li>agentA2A / jobInfo：传 null（A2A 类型暂不深度克隆配置）</li>
     * </ul>
     */
    private AgentDefinitionVO buildClonedVo(AgentDefinition source, Long callerTenantId) {
        AgentDefinitionVO vo = new AgentDefinitionVO();
        // 同名属性直接拷贝
        BeanUtils.copyProperties(source, vo);
        // 覆盖关键字段
        vo.setId(null);
        vo.setTenantId(callerTenantId);
        vo.setName(source.getName() + AgentCloneConstants.COPY_NAME_SUFFIX);
        vo.setAgentCode(buildCopyCode(source.getAgentCode()));
        vo.setEnabled(false);
        // 重置跨租户不可访问的资源引用
        vo.setModelConfigId(null);
        vo.setSystemPromptTemplateId(null);
        vo.setSensitiveWordConfigId(null);
        // 子项统一传空（不复制 mcp / skill / knowledge / studio / codeExecution / longTerm）
        vo.setHook(Collections.emptyList());
        vo.setTool(Collections.emptyList());
        vo.setMcp(Collections.emptyList());
        vo.setMcpBindings(Collections.emptyList());
        vo.setSkill(Collections.emptyList());
        vo.setSubAgent(Collections.emptyList());
        vo.setKnowledgeBase(Collections.emptyList());
        vo.setStudioConfigId(null);
        vo.setCodeExecutionConfigId(null);
        vo.setLongTermMemoryConfigId(null);
        // A2A 类型：暂不复用 a2aConfig，避免引用 default 租户的 a2a 配置
        if (source.getAgentType() == AgentType.A2A) {
            vo.setAgentA2A(null);
        }
        // 审计字段：置 null 让 MyBatis-Plus 重填
        vo.setCreatedAt(null);
        vo.setUpdatedAt(null);
        vo.setCreatedBy(null);
        vo.setUpdatedBy(null);
        // used / jobInfo：业务占位
        vo.setUsed(null);
        vo.setJobInfo(null);
        return vo;
    }

    /**
     * 生成克隆副本的 agent_code
     * <p>
     * 形如 {@code <源code>-copy-<uuid8>}，UUID8 避免命名冲突。
     */
    private String buildCopyCode(String sourceCode) {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, AgentCloneConstants.UUID_SUFFIX_LENGTH);
        return sourceCode + AgentCloneConstants.COPY_CODE_SEPARATOR + suffix;
    }
}
