package com.hxh.apboa.assessment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hxh.apboa.common.entity.KnowledgeBaseConfig;
import org.apache.ibatis.annotations.Mapper;

/** AI 阅卷专用只读映射：知识库配置（不读取 connection_config 等敏感字段内容）。 */
@Mapper
public interface GradingKbConfigMapper extends BaseMapper<KnowledgeBaseConfig> {}
