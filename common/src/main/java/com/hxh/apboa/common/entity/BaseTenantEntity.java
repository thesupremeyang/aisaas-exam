package com.hxh.apboa.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;

/**
 * 多租户基础实体类，继承 BaseEntity 并添加租户ID字段
 *
 * @author huxuehao
 */
@Getter
@Setter
public abstract class BaseTenantEntity extends BaseEntity {

    /**
     * 租户ID
     */
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;
}
