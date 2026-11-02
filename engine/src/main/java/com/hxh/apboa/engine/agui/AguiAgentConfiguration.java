package com.hxh.apboa.engine.agui;

import com.hxh.apboa.engine.agent.IAgentFactory;
import io.agentscope.core.agui.registry.AguiAgentRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 描述：AGUI智能体注册
 *
 * @author huxuehao
 **/
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AguiAgentConfiguration implements ApplicationRunner {
    private final IAgentFactory iAgentFactory;
    private final AguiAgentRegistry registry;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 本 JVM 已注册的 agentCode 镜像。
     *
     * <p>registry 来自 agentscope 依赖包，没有「只查存在性」的接口；
     * 直接 getAgent() 会触发工厂实例化，不能在热路径上用。
     * 这里自己维护一份集合，与 register/unregister 同步。</p>
     */
    private final Set<String> registeredCodes = ConcurrentHashMap.newKeySet();

    @Override
    public void run(ApplicationArguments args) {
        log.info("Re-registering all agents...");
        reRegisterAgent(null);
        log.info("Re-registering all agents completed.");
    }

    /**
     * 重新注册智能体
     * @param agentId 智能体定义，为空则重新注册所有智能体
     */
    public void reRegisterAgent(Long agentId) {
        if (registry == null) return;

        try {
            // 使用 jdbcTemplate 绕过租户拦截器（启动时无租户上下文）
            String sql = "SELECT id, agent_code, tenant_id FROM agent_definition WHERE enabled = true";
            if (agentId != null) {
                sql += " AND id = " + agentId;
            }
            jdbcTemplate.query(sql, (rs) -> {
                Long id = rs.getLong("id");
                String agentCode = rs.getString("agent_code");
                Long tenantId = rs.getLong("tenant_id");
                // 注销已注册的智能体
                unregisterAgent(agentCode);
                // 注册智能体
                registry.registerFactory(agentCode,
                        () -> iAgentFactory.getAgent(id, tenantId));
                registeredCodes.add(agentCode);
            });
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 注销单个智能体注册
     *
     * @param agentCode 智能体Code
     */
    public void unregisterAgent(String agentCode) {
        try {
            registry.unregister(agentCode);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            if (agentCode != null) {
                registeredCodes.remove(agentCode);
            }
        }
    }

    /**
     * 按 agentCode 补注册单个智能体（请求侧自愈）。
     *
     * <p>平台新建智能体后依赖 Redis pub/sub 通知 runtime 注册，而 pub/sub 是「最多一次」投递：
     * 订阅未就绪、Redis 抖动、消息落在重启窗口里，都会让该智能体一直 404，
     * 直到人工 {@code PUBLISH apboa:agent:cluster:reRegister}。这里提供按 code 的同步补注册，
     * 代价只有一次索引查询。</p>
     *
     * @param agentCode 智能体编码
     */
    public void reRegisterAgentByCode(String agentCode) {
        if (registry == null || agentCode == null || agentCode.isEmpty()) {
            return;
        }
        try {
            jdbcTemplate.query(
                    "SELECT id, agent_code, tenant_id FROM agent_definition "
                            + "WHERE enabled = true AND agent_code = ?",
                    rs -> {
                        long id = rs.getLong("id");
                        long tenantId = rs.getLong("tenant_id");
                        registry.registerFactory(agentCode, () -> iAgentFactory.getAgent(id, tenantId));
                        registeredCodes.add(agentCode);
                    },
                    agentCode);
            log.info("Self-healed agent registration for {}", agentCode);
        } catch (Exception e) {
            log.error("Failed to self-heal agent {}: {}", agentCode, e.getMessage());
        }
    }

    /**
     * 周期性对账：把库里 enabled 但注册表中缺失的智能体补注册回来。
     *
     * <p>作为 pub/sub 丢消息的最终兜底。默认每 2 分钟一次，只做存在性比较，
     * 已注册的智能体不会被重建。</p>
     */
    @Scheduled(
            initialDelayString = "${apboa.agent.reconcile.initial-delay-ms:60000}",
            fixedDelayString = "${apboa.agent.reconcile.interval-ms:120000}")
    public void reconcile() {
        if (registry == null) {
            return;
        }
        try {
            List<Object[]> rows =
                    jdbcTemplate.query(
                            "SELECT id, agent_code, tenant_id FROM agent_definition WHERE enabled = true",
                            (rs, rowNum) ->
                                    new Object[] {
                                        rs.getLong("id"),
                                        rs.getString("agent_code"),
                                        rs.getLong("tenant_id")
                                    });
            int healed = 0;
            for (Object[] row : rows) {
                String code = (String) row[1];
                if (code == null || code.isEmpty() || registeredCodes.contains(code)) {
                    continue;
                }
                long id = (Long) row[0];
                long tenantId = (Long) row[2];
                registry.registerFactory(code, () -> iAgentFactory.getAgent(id, tenantId));
                registeredCodes.add(code);
                healed++;
                if (healed <= 10) {
                    log.info("Agent reconcile: registered missing agent {}", code);
                }
            }
            if (healed > 0) {
                log.warn(
                        "Agent reconcile: registered {} missing agent(s) out of {}",
                        healed,
                        rows.size());
            }
        } catch (Exception e) {
            log.error("Agent reconcile failed: {}", e.getMessage());
        }
    }
}
