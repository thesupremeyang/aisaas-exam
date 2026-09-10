package com.hxh.apboa.common.wrapper;

import com.hxh.apboa.common.enums.ScopeType;
import lombok.*;

import java.util.List;

/**
 * 描述：钩子配置包装类
 *
 * @author huxuehao
 **/
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HookConfigWrapper {
    String name;
    String description;
    String classPath;

    /**
     * 作用域类型（null 视为 GLOBAL）
     */
    ScopeType scopeType;

    /**
     * 目标租户编码列表（仅在 scopeType = TENANT 时生效）
     */
    List<String> tenantCodes;
}
