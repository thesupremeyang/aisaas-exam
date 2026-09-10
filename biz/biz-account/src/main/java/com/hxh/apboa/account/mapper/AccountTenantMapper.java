package com.hxh.apboa.account.mapper;

import com.hxh.apboa.common.entity.AccountTenant;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 账号-租户关联 Mapper
 *
 * @author huxuehao
 */
@Mapper
public interface AccountTenantMapper extends BaseMapper<AccountTenant> {
}
