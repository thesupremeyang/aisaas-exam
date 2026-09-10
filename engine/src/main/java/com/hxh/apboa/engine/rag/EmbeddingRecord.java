package com.hxh.apboa.engine.rag;

/**
 * 嵌入向量记录
 *
 * @author huxuehao
 */
public record EmbeddingRecord(Long id, Long chunkId, Long documentId,
                              Long knowledgeBaseConfigId, Long tenantId, float[] embedding) {
}
