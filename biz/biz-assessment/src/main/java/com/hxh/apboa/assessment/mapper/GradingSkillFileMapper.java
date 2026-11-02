package com.hxh.apboa.assessment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hxh.apboa.common.entity.SkillFile;
import org.apache.ibatis.annotations.Mapper;

/** AI 阅卷专用只读映射：Skill 文件（SCRIPTS 仅读取内容作证据，绝不执行）。 */
@Mapper
public interface GradingSkillFileMapper extends BaseMapper<SkillFile> {}
