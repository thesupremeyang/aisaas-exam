package com.hxh.apboa.assessment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hxh.apboa.common.entity.RagDocument;
import org.apache.ibatis.annotations.Mapper;

/** AI 阅卷专用只读映射：RAG 文档。 */
@Mapper
public interface GradingRagDocumentMapper extends BaseMapper<RagDocument> {}
