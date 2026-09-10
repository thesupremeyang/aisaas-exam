package com.hxh.apboa.console.v2portal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hxh.apboa.agent.mapper.IJobInfoMapper;
import com.hxh.apboa.console.v2portal.clone.mapper.AgentCrossTenantReadMapper;
import com.hxh.apboa.common.consts.CreatorAgentConst;
import com.hxh.apboa.agent.service.AgentDefinitionService;
import com.hxh.apboa.common.dto.AgentDefinitionDTO;
import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.entity.AgentLongTermMemory;
import com.hxh.apboa.common.entity.AgentStudio;
import com.hxh.apboa.common.entity.JobInfo;
import com.hxh.apboa.common.mp.support.MP;
import com.hxh.apboa.common.mp.support.PageParams;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.common.util.BeanUtils;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.common.vo.AgentDefinitionVO;
import com.hxh.apboa.longterm.mapper.AgentLongTermMemoryMapper;
import com.hxh.apboa.studio.mapper.AgentStudioMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * v2 门户 - 智能体广场 Controller
 *
 * <p>产品需求：智能体广场固定展示 tenant_id=1 的智能体，不受当前登录用户所在租户影响。
 * 实现思路：通过 {@link TenantUtils} 临时切换 ThreadLocal 中的租户上下文，
 * 让 MyBatis-Plus 的 {@code TenantLineInnerInterceptor} 自动追加
 * {@code AND tenant_id = 1} 条件；调用结束后在 finally 块中还原，
 * 避免污染同一线程后续请求。</p>
 *
 * <p>本 Controller 完全独立，<strong>不修改</strong>原有的
 * {@code AgentDefinitionController} 一行代码。enrich 逻辑与原 page 接口保持一致，
 * 通过直接复制实现（业务字段变更时需同步两处）。</p>
 *
 * @author huxuehao
 */
@Slf4j
@RestController
@RequestMapping("/v2-portal/explore")
@RequiredArgsConstructor
public class V2PortalExploreController {

    /** 智能体广场固定租户 ID（产品要求：无论登录用户所属租户都展示该租户的智能体） */
    private static final long PORTAL_FIXED_TENANT_ID = 1L;

    private final AgentDefinitionService agentDefinitionService;
    private final IJobInfoMapper iJobInfoMapper;
    private final AgentStudioMapper agentStudioMapper;
    private final AgentLongTermMemoryMapper agentLongTermMemoryMapper;
    private final AgentCrossTenantReadMapper crossTenantReadMapper;

    /**
     * 智能体广场分页查询
     *
     * <p>行为与原 {@code /api/agent/definition/page} 一致，但租户过滤条件
     * 被强制设为 {@code tenant_id = 1}。支持入参 name / tag / agentType 等搜索条件。</p>
     */
    @GetMapping("/page")
    public R<IPage<AgentDefinitionVO>> page(PageParams pageParams, AgentDefinitionDTO query) {
        Long originalTenantId = TenantUtils.getCurrentTenantId();
        String originalTenantCode = TenantUtils.getCurrentTenantCode();
        try {
            // 临时切换到目标租户上下文
            TenantUtils.setCurrentTenant(PORTAL_FIXED_TENANT_ID, null);

            // 调原 service，TenantLineInnerInterceptor 会自动追加 tenant_id=1
            IPage<AgentDefinition> page = agentDefinitionService.page(
                    MP.getPage(pageParams),
                    MP.getQueryWrapper(query)
            );
            IPage<AgentDefinitionVO> pageVo = BeanUtils.copyPage(page, AgentDefinitionVO.class);

            // enrich：与原 AgentDefinitionController.page 中的逻辑完全一致
            enrichAgentDefinitionVO(pageVo);

            return R.data(pageVo);
        } catch (Exception e) {
            log.error("[v2-portal] 智能体广场分页查询失败", e);
            return R.fail("查询失败：" + e.getMessage());
        } finally {
            // 还原：避免污染同一线程后续请求的租户上下文
            TenantUtils.setCurrentTenant(originalTenantId, originalTenantCode);
        }
    }

    /**
     * 智能体广场智能体详情（强制 tenant_id=1）
     */
    @GetMapping("/detail/{id}")
    public R<AgentDefinition> detail(@PathVariable("id") Long id) {
        Long originalTenantId = TenantUtils.getCurrentTenantId();
        String originalTenantCode = TenantUtils.getCurrentTenantCode();
        try {
            TenantUtils.setCurrentTenant(PORTAL_FIXED_TENANT_ID, null);
            return R.data(agentDefinitionService.getById(id));
        } catch (Exception e) {
            log.error("[v2-portal] 智能体详情查询失败", e);
            return R.fail("查询失败：" + e.getMessage());
        } finally {
            TenantUtils.setCurrentTenant(originalTenantId, originalTenantCode);
        }
    }

    /**
     * Returns only the fixed platform creator agent. This intentionally avoids
     * exposing a general cross-tenant agent lookup endpoint.
     */
    @GetMapping("/creator")
    public R<AgentDefinition> creator() {
        AgentDefinition creator =
                crossTenantReadMapper.selectByIdIgnoreTenant(CreatorAgentConst.AGENT_ID);
        if (creator == null || !Boolean.TRUE.equals(creator.getEnabled())) {
            return R.fail("智能体创建助手暂不可用");
        }
        return R.data(creator);
    }

    /**
     * 获取智能体广场标签列表（强制 tenant_id=1）
     */
    @GetMapping("/tags")
    public R<List<String>> listTags() {
        Long originalTenantId = TenantUtils.getCurrentTenantId();
        String originalTenantCode = TenantUtils.getCurrentTenantCode();
        try {
            TenantUtils.setCurrentTenant(PORTAL_FIXED_TENANT_ID, null);
            return R.data(agentDefinitionService.listTags());
        } catch (Exception e) {
            log.error("[v2-portal] 标签列表查询失败", e);
            return R.fail("查询失败：" + e.getMessage());
        } finally {
            TenantUtils.setCurrentTenant(originalTenantId, originalTenantCode);
        }
    }

    /**
     * 补全 JobInfo / Studio / LongTermMemory 字段
     * <p>与 AgentDefinitionController.page 内联逻辑保持一致；如原逻辑变更需同步修改本方法。</p>
     */
    private void enrichAgentDefinitionVO(IPage<AgentDefinitionVO> pageVo) {
        if (pageVo == null || pageVo.getRecords() == null || pageVo.getRecords().isEmpty()) {
            return;
        }
        List<JobInfo> jobs = iJobInfoMapper.selectList(
                new LambdaQueryWrapper<JobInfo>().eq(JobInfo::getType, "AGENT"));
        List<AgentStudio> studios = agentStudioMapper.selectList(null);
        List<AgentLongTermMemory> memories = agentLongTermMemoryMapper.selectList(null);

        if (jobs.isEmpty() && studios.isEmpty() && memories.isEmpty()) {
            return;
        }

        Map<String, JobInfo> jobMap = jobs.stream().collect(Collectors.toMap(
                JobInfo::getBizId,
                item -> item,
                (existing, replacement) -> existing));
        Map<Long, Long> studioMap = studios.stream().collect(Collectors.toMap(
                AgentStudio::getAgentDefinitionId,
                AgentStudio::getStudioId,
                (existing, replacement) -> existing));
        Map<Long, Long> memoryMap = memories.stream().collect(Collectors.toMap(
                AgentLongTermMemory::getAgentDefinitionId,
                AgentLongTermMemory::getLongTermMemoryConfigId,
                (existing, replacement) -> existing));

        pageVo.getRecords().forEach(vo -> {
            if (jobMap.containsKey(String.valueOf(vo.getId()))) {
                vo.setJobInfo(jobMap.get(String.valueOf(vo.getId())));
            }
            if (studioMap.containsKey(vo.getId())) {
                vo.setStudioConfigId(studioMap.get(vo.getId()));
            }
            if (memoryMap.containsKey(vo.getId())) {
                vo.setLongTermMemoryConfigId(memoryMap.get(vo.getId()));
            }
        });
    }
}
