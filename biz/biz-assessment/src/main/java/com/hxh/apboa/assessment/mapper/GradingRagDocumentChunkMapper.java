package com.hxh.apboa.assessment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hxh.apboa.common.entity.RagDocumentChunk;
import org.apache.ibatis.annotations.Mapper;

/** AI 阅卷专用只读映射：RAG 文档切片。 */
@Mapper
public interface GradingRagDocumentChunkMapper extends BaseMapper<RagDocumentChunk> {}
