package com.hxh.apboa.scheduler.scheduler;

import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.common.wrapper.AgentJobWrapper;
import com.hxh.apboa.engine.agent.IAgentFactory;
import com.hxh.apboa.scheduler.consts.JobConst;
import com.hxh.apboa.scheduler.core.job.QuartzJob;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.message.Msg;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;

/**
 * 描述：智能体任务
 *
 * @author huxuehao
 **/
@Slf4j
public class AgentScheduler extends QuartzJob {
    @Override
    public Object doJob(JobExecutionContext context) {
        AgentJobWrapper wrapper = getDataMap(JobConst.DATA_MAP_KEY, AgentJobWrapper.class);
        Long tenantId = getDataMap(JobConst.TENANT_ID_KEY, Long.class);
        if (wrapper == null || tenantId == null) {
            return false;
        }

        String agentId = wrapper.getAgentId();
        if (agentId == null || agentId.trim().isEmpty()) {
            return false;
        }

        try {
            IAgentFactory agentFactory = getBean(IAgentFactory.class);
            Agent agent = agentFactory.getAgent(Long.valueOf(agentId.trim()), tenantId);
            agent.call(
                    Msg.builder()
                            .textContent(wrapper.getInput())
                            .build())
                    .block();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return false;
        } finally {
            // 设置租户信息，恢复因agentFactory.getAgent中清除的租户信息
            // 不用担心内存泄漏, 调用此方法的方法中会清除租户信息
            TenantUtils.setCurrentTenant(tenantId, null);
        }
        return true;
    }
}
