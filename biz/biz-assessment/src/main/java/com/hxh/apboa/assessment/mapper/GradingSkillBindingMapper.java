package com.hxh.apboa.assessment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hxh.apboa.common.entity.AgentSkillPackage;
import org.apache.ibatis.annotations.Mapper;

/** AI 阅卷专用只读映射：智能体-Skill 包绑定。 */
@Mapper
public interface GradingSkillBindingMapper extends BaseMapper<AgentSkillPackage> {}
