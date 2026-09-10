package com.hxh.apboa.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.consts.TableConst;
import lombok.Getter;
import lombok.Setter;

/**
 * 租户表
 *
 * @author huxuehao
 */
@Getter
@Setter
@TableName(TableConst.TENANT)
public class Tenant extends BaseEntity {
    private String name;
    private String code;
    private String description;
    private Integer status;
    private String contactName;
    private String contactEmail;
    private String config;
    /** 是否允许被非租户用户发现 */
    private Boolean discoverable;
    /** 是否允许用户主动申请加入 */
    private Boolean joinable;
    /** 加入是否需要管理员审批 */
    private Boolean joinApprovalRequired;
}
