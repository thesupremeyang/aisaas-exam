package com.hxh.apboa.engine.hook.dynamices;

import com.hxh.apboa.engine.InstanceLoader;
import io.agentscope.core.hook.Hook;

/**
 * 描述：HookInstanceLoader
 *
 * @author huxuehao
 **/
public interface HookInstanceLoader extends InstanceLoader<Hook> {
    /**
     * 初始化
     */
    @Override
    default void afterSingletonsInstantiated() {
        // 完成注册
        HookInstanceLoadFactory.registerLoader(this);
    };
}
