package com.hxh.apboa.engine.hook;

import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.entity.HookConfig;
import com.hxh.apboa.common.enums.CodeLanguage;
import com.hxh.apboa.common.enums.HookType;
import com.hxh.apboa.engine.hook.dynamices.HookInstanceLoadFactory;
import com.hxh.apboa.engine.log.ChatLogHook;
import com.hxh.apboa.engine.workspace.hook.WorkspaceHook;
import com.hxh.apboa.engine.workspace.hook.WebsocketHook;
import com.hxh.apboa.hook.service.AgentHookService;
import com.hxh.apboa.hook.service.HookConfigService;
import io.agentscope.core.hook.Hook;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 描述：钩子工厂
 *
 * @author huxuehao
 **/
@Component
@RequiredArgsConstructor
public class HooksFactory {
    private final AgentHookService agentHookService;
    private final HookConfigService hookConfigService;
    private final WebsocketHook websocketHook;

    public List<Hook> getHooks(AgentDefinition agentDefinition) {
        List<Hook> hooks = new ArrayList<>();

        // 配置工作空间专属Hook
        hooks.add(new WorkspaceHook());
        hooks.add(websocketHook);

        List<Long> hookIds = agentHookService.getHookIds(agentDefinition.getId());
        if (hookIds.isEmpty()) {
            // 聊天记录钩子（放到最后）
            hooks.add(new ChatLogHook());
            return hooks;
        }

        hookConfigService.listByIds(hookIds)
                .stream()
                .filter(HookConfig::getEnabled)
                .forEach(hookConfig -> {
                    Hook hook;
                    if (hookConfig.getHookType() == HookType.BUILTIN) {
                        hook = HooksRegister.getHook(hookConfig.getClassPath());
                    } else {
                        hook = HookInstanceLoadFactory.getInstanceLoader(CodeLanguage.JAVA)
                                .loadInstance(hookConfig.getCode());
                    }
                    hooks.add(hook);
                });

        // 聊天记录钩子（放到最后）
        hooks.add(new ChatLogHook());
        return hooks;
    }
}
