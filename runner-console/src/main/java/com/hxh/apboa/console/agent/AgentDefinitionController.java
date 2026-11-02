package com.hxh.apboa.console.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hxh.apboa.agent.mapper.IJobInfoMapper;
import com.hxh.apboa.agent.service.AgentDefinitionService;
import com.hxh.apboa.common.cluster.core.MessagePublisher;
import com.hxh.apboa.common.config.auth.ChatKeyAccess;
import com.hxh.apboa.common.config.auth.RoleNeed;
import com.hxh.apboa.common.config.auth.SkAccess;
import com.hxh.apboa.common.consts.RedisChannelTopic;
import com.hxh.apboa.common.dto.AgentDefinitionDTO;
import com.hxh.apboa.common.entity.*;
import com.hxh.apboa.common.enums.TenantRole;
import com.hxh.apboa.common.exception.BusinessException;
import com.hxh.apboa.common.mp.support.MP;
import com.hxh.apboa.common.mp.support.PageParams;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.common.util.BeanUtils;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.common.util.UserUtils;
import com.hxh.apboa.common.vo.AgentDefinitionVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hxh.apboa.studio.mapper.AgentStudioMapper;
import com.hxh.apboa.longterm.mapper.AgentLongTermMemoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 智能体定义Controller
 *
 * @author huxuehao
 */
@RestController
@RequestMapping("/agent/definition")
@RequiredArgsConstructor
public class AgentDefinitionController {

    private final AgentDefinitionService agentDefinitionService;
    private final IJobInfoMapper iJobInfoMapper;
    private final AgentStudioMapper agentStudioMapper;
    private final AgentLongTermMemoryMapper agentLongTermMemoryMapper;
    private final MessagePublisher messagePublisher;

    /**
     * 分页查询。
     * viewTenantId：平台管理员专用——从「答卷管理」进入考生智能体配置界面时按考生租户过滤
     * （管理员带跨租户豁免，不传则返回全平台数据；非平台管理员忽略该参数，仍受租户隔离约束）。
     */
    @GetMapping("/page")
    public R<IPage<AgentDefinitionVO>> page(PageParams pageParams, AgentDefinitionDTO query,
            @RequestParam(value = "viewTenantId", required = false) Long viewTenantId) {
        QueryWrapper<AgentDefinition> wrapper = MP.getQueryWrapper(query);
        if (viewTenantId != null && UserUtils.isPlatformAdmin()) {
            wrapper.eq("tenant_id", viewTenantId);
        }
        IPage<AgentDefinition> page = agentDefinitionService.page(MP.getPage(pageParams), wrapper);
        IPage<AgentDefinitionVO> pageVo = BeanUtils.copyPage(page, AgentDefinitionVO.class);
        List<JobInfo> agent = iJobInfoMapper.selectList(new LambdaQueryWrapper<JobInfo>().eq(JobInfo::getType, "AGENT"));
        List<AgentStudio> agentStudios = agentStudioMapper.selectList(null);
        List<AgentLongTermMemory> agentLongTermMemories = agentLongTermMemoryMapper.selectList(null);
        if (!agent.isEmpty() || !agentStudios.isEmpty() || !agentLongTermMemories.isEmpty()) {
            Map<String, JobInfo> collectMap = agent.stream().collect(Collectors.toMap(
                    JobInfo::getBizId,
                    item -> item,
                    (existing, replacement) -> existing));
            Map<Long, Long> agentStudioMap = agentStudios.stream().collect(Collectors.toMap(
                    AgentStudio::getAgentDefinitionId,
                    AgentStudio::getStudioId,
                    (existing, replacement) -> existing));
            Map<Long, Long> agentLongTermMemoryMap = agentLongTermMemories.stream().collect(Collectors.toMap(
                    AgentLongTermMemory::getAgentDefinitionId,
                    AgentLongTermMemory::getLongTermMemoryConfigId,
                    (existing, replacement) -> existing));
            pageVo.getRecords().forEach(agentVo -> {
                if (collectMap.containsKey(String.valueOf(agentVo.getId()))) {
                    agentVo.setJobInfo(collectMap.get(String.valueOf(agentVo.getId())));
                }
                if (agentStudioMap.containsKey(agentVo.getId())) {
                    agentVo.setStudioConfigId(agentStudioMap.get(agentVo.getId()));
                }
                if (agentLongTermMemoryMap.containsKey(agentVo.getId())) {
                    agentVo.setLongTermMemoryConfigId(agentLongTermMemoryMap.get(agentVo.getId()));
                }
            });
        }

        return R.data(pageVo);
    }

    /**
     * 详情（路径参数兼容数字 id 与 agentCode）
     */
    @SkAccess
    @ChatKeyAccess
    @GetMapping("/{id}")
    public R<AgentDefinitionVO> detail(@PathVariable("id") String idOrCode) {
        Long id = requireAgentId(idOrCode);
        AgentDefinitionVO vo = agentDefinitionService.agentDefinitionDetail(id);
        if (vo == null) {
            return R.data(null);
        }
        vo.setUsed(agentDefinitionService.usedWithAgent(List.of(id)));
        List<JobInfo> agent = iJobInfoMapper.selectList(
                new LambdaQueryWrapper<JobInfo>()
                        .eq(JobInfo::getType, "AGENT")
                        .eq(JobInfo::getBizId, String.valueOf(id)));
        if (agent.size() == 1) {
            vo.setJobInfo(agent.getFirst());
        }

        return R.data(vo);
    }

    /**
     * 新增
     */
    @PostMapping
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<Boolean> save(@RequestBody AgentDefinitionVO vo) {
        // Never trust a tenant id supplied by an agent/tool request. New agents
        // always belong to the authenticated user's active SaaS tenant.
        vo.setTenantId(TenantUtils.getCurrentTenantId());
        agentDefinitionService.saveAgentDefinition(vo);
        messagePublisher.publishAfterCommit(RedisChannelTopic.AGENT_REREGISTER_CHANNEL, String.valueOf(vo.getId()));
        return R.data(true);
    }

    /**
     * 修改
     */
    @PutMapping
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<Boolean> update(@RequestBody AgentDefinitionVO vo) {
        vo.setTenantId(TenantUtils.getCurrentTenantId());
        return R.data(agentDefinitionService.updateAgentDefinition(vo));
    }

    /**
     * 删除
     */
    @DeleteMapping
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<Boolean> delete(@RequestBody List<Long> ids) {
        return R.data(agentDefinitionService.deleteAgentDefinition(ids));
    }

    /**
     * 被哪些Agent使用
     */
    @PostMapping("used-with-agent")
    public R<List<Object>> usedWithAgent(@RequestBody List<Long> ids) {
        return R.data(agentDefinitionService.usedWithAgent(ids));
    }

    /**
     * 获取所有Tag
     */
    @GetMapping("/get/tags")
    public R<List<String>> listTags() {
        return R.data(agentDefinitionService.listTags());
    }

    @SkAccess
    @ChatKeyAccess
    @GetMapping("/{id}/allow/file-type")
    public R<List<String>> allowFileType(@PathVariable("id") String idOrCode) {
        return R.data(agentDefinitionService.allowFileType(requireAgentId(idOrCode)));
    }

    /**
     * 获取Agent启用的工具
     */
    @SkAccess
    @ChatKeyAccess
    @GetMapping("/{agentId}/enabled/tools")
    public R<List<ToolConfig>> getEnabledToolsOfAgent(@PathVariable("agentId") String agentIdOrCode) {
        return R.data(agentDefinitionService.getEnabledToolsOfAgent(requireAgentId(agentIdOrCode)));
    }

    /**
     * 获取Agent启用的技能包
     */
    @SkAccess
    @ChatKeyAccess
    @GetMapping("/{agentId}/enabled/skills")
    public R<List<SkillPackage>> getEnabledSkillsOfAgent(@PathVariable("agentId") String agentIdOrCode) {
        return R.data(agentDefinitionService.getEnabledSkillsOfAgent(requireAgentId(agentIdOrCode)));
    }

    private Long requireAgentId(String idOrCode) {
        Long id = agentDefinitionService.resolveAgentId(idOrCode);
        if (id == null) {
            throw new BusinessException("智能体不存在或已禁用: " + idOrCode);
        }
        return id;
    }
}
