package com.hxh.apboa.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 长期记忆类型
 *
 * @author huxuehao
 */
@Getter
@AllArgsConstructor
public enum LongTermMemoryType {
    MEM0("Mem0 长期记忆"),
    REME("ReMe 长期记忆（阿里通义千问）"),
    BAILIAN("百炼记忆库（阿里云百炼）");

    private final String description;
}
