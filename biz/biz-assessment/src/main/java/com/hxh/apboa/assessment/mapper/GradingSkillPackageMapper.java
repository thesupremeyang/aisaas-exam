package com.hxh.apboa.assessment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hxh.apboa.common.entity.SkillPackage;
import org.apache.ibatis.annotations.Mapper;

/** AI 阅卷专用只读映射：Skill 包。 */
@Mapper
public interface GradingSkillPackageMapper extends BaseMapper<SkillPackage> {}
