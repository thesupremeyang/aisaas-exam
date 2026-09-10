package com.hxh.apboa.common.config.auth;

import com.hxh.apboa.common.enums.TenantRole;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 描述：需要的租户角色（采用等级比较：用户角色等级 >= 所需角色等级即通过）
 *
 * @author huxuehao
 **/
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RoleNeed {
    TenantRole[] value();
}
