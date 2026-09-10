package com.hxh.apboa.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.hxh.apboa.common.config.mybatis.JsonNodeTypeHandler;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.enums.LongTermMemoryType;
import lombok.Getter;
import lombok.Setter;

/**
 * 长期记忆配置
 *
 * @author huxuehao
 */
@Getter
@Setter
@TableName(value = TableConst.LONG_TERM_MEMORY_CONFIG, autoResultMap = true)
public class LongTermMemoryConfig extends BaseTenantEntity {
    /**
     * 配置名称
     */
    private String configName;

    /**
     * 记忆类型：MEM0/REME/BAILIAN
     */
    private LongTermMemoryType memoryType;

    /**
     * 配置详情JSON
     */
    @TableField(typeHandler = JsonNodeTypeHandler.class)
    private JsonNode config;
}
