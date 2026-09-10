package com.hxh.apboa.common.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 租户加入申请请求
 *
 * @author huxuehao
 */
@Data
public class TenantJoinRequestDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String message;
}
