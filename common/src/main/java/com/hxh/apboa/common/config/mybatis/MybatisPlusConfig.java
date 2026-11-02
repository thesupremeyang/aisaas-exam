package com.hxh.apboa.common.config.mybatis;

import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.util.TenantUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * 描述：MyBatisPlus 配置
 *
 * @author huxuehao
 **/
@Configuration
public class MybatisPlusConfig {

    private static final Set<String> IGNORE_TABLES = Set.of(
            TableConst.TENANT,         // 租户元数据表（B类）
            TableConst.ACCOUNT,        // 账号表（B类，跨租户共享）
            TableConst.ACCOUNT_TENANT,  // 账号-租户关联表（自管理）
            TableConst.AGENT_SCOPE_SESSIONS, // 会话表（B类，跨租户共享）由agentscope提供
            TableConst.SKILL_TOKEN,    // 内部服务鉴权 token（跨服务共享）
            TableConst.AGENT_CHAT_KEY,  // 会话密钥表（B类，跨租户共享）
            "agent_clone_record",      // 广场克隆审计表（跨租户共享）
            TableConst.EXAM_CAMPAIGN,   // 考试批次（平台级活动，对所有租户可见）
            TableConst.EXAM_QUESTION,   // 考试试题（挂在考试批次下，由平台 admin 统一管理）
            TableConst.AI_GRADING_SETTINGS // AI 阅卷模型配置（平台级全局单行配置）
    );

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 添加租户隔离拦截器（最先执行）
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                Long tenantId = TenantUtils.getCurrentTenantId();
                if (tenantId == null) {
                    return null;
                }
                return new LongValue(tenantId);
            }

            @Override
            public String getTenantIdColumn() {
                return "tenant_id";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                // 后台任务显式声明忽略租户时，所有表均不附加租户条件
                if (TenantUtils.isIgnoreTenant()) {
                    return true;
                }
                // B类表不添加租户过滤
                return IGNORE_TABLES.contains(tableName.toLowerCase());
            }
        }));

        // 添加分页拦截器
        interceptor.addInnerInterceptor(InterceptorFactory.createPaginationInnerInterceptor());
        return interceptor;
    }
}
