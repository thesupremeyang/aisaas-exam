package com.hxh.apboa.assessment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hxh.apboa.common.entity.AgentKnowledgeBase;
import org.apache.ibatis.annotations.Mapper;

/** AI 阅卷专用只读映射：智能体-知识库绑定（biz-knowledge 不在 console 依赖内，独立命名避免冲突）。 */
@Mapper
public interface GradingKbBindingMapper extends BaseMapper<AgentKnowledgeBase> {}
