package com.hxh.apboa.engine.rag.store.impl;

import com.hxh.apboa.engine.rag.EmbeddingRecord;
import com.hxh.apboa.engine.rag.RetrievalResult;
import com.hxh.apboa.engine.rag.store.VectorStore;
import com.hxh.apboa.common.util.TenantUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PgVector向量存储服务
 *
 * @author huxuehao
 */
@Component
@ConditionalOnProperty(name = "rag.store", havingValue = "pgvector")
public class PgVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(PgVectorStore.class);

    /**
     * 支持的向量维度列表
     */
    private static final int[] SUPPORTED_DIMENSIONS = {64, 128, 256, 512, 768, 1024, 2048, 2560};

    private final JdbcTemplate pgJdbcTemplate;

    public PgVectorStore(@Autowired(required = false) @Qualifier("pgVectorDataSource") DataSource pgVectorDataSource) {
        if (pgVectorDataSource != null) {
            this.pgJdbcTemplate = new JdbcTemplate(pgVectorDataSource);
            initSchema();
        } else {
            this.pgJdbcTemplate = null;
            log.warn("PgVector数据源未配置，本地RAG功能不可用");
        }
    }

    /**
     * 根据向量维度拼接表名
     */
    private String getTableName(int dimension) {
        return "rag_embedding_" + dimension;
    }

    /**
     * 初始化pgvector扩展和多维度表结构
     */
    private void initSchema() {
        try {
            pgJdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
        } catch (Exception e) {
            log.error("PgVector扩展初始化失败", e);
            return;
        }
        for (int dim : SUPPORTED_DIMENSIONS) {
            try {
                initTable(dim);
            } catch (Exception e) {
                log.warn("维度{}的表结构初始化失败，跳过: {}", dim, e.getMessage());
            }
        }
        log.info("PgVector表结构初始化完成");
    }

    private void initTable(int dim) {
        String embeddingType = dim > 2000 ? "halfvec" : "vector";
        String tableName = getTableName(dim);
        pgJdbcTemplate.execute(String.format("""
            CREATE TABLE IF NOT EXISTS %s (
                id BIGINT PRIMARY KEY,
                chunk_id BIGINT NOT NULL,
                document_id BIGINT NOT NULL,
                knowledge_base_config_id BIGINT NOT NULL,
                tenant_id BIGINT NOT NULL DEFAULT 1,
                embedding %s(%d) NOT NULL,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
        """, tableName, embeddingType, dim));
        safeCreateIndex(tableName, "idx_embedding_kbc_" + dim, "knowledge_base_config_id");
        safeCreateIndex(tableName, "idx_embedding_doc_" + dim, "document_id");
        safeCreateIndex(tableName, "idx_embedding_tenant_" + dim, "tenant_id");
        safeCreateIndex(tableName, "idx_rag_vectors_embedding_" + dim, "embedding", embeddingType);
    }

    private void safeCreateIndex(String tableName, String indexName, String column) {
        try {
            pgJdbcTemplate.execute("CREATE INDEX IF NOT EXISTS " + indexName + " ON " + tableName + "(" + column + ")");
        } catch (Exception e) {
            log.warn("创建索引 {} 失败: {}", indexName, e.getMessage());
        }
    }

    private void safeCreateIndex(String tableName, String indexName, String column, String embeddingType) {
        try {
            pgJdbcTemplate.execute("CREATE INDEX IF NOT EXISTS " + indexName + " ON " + tableName + " USING hnsw (embedding " + embeddingType + "_cosine_ops)");
        } catch (Exception e) {
            log.warn("创建HNSW索引 {} 失败: {}", indexName, e.getMessage());
        }
    }

    @Override
    public boolean isAvailable() {
        return pgJdbcTemplate != null;
    }

    @Override
    public void storeEmbedding(Long id, Long chunkId, Long documentId,
                               Long knowledgeBaseConfigId, float[] embedding) {
        if (!isAvailable()) {
            throw new RuntimeException("PgVector数据源未配置");
        }

        int dimension = embedding.length;
        String tableName = getTableName(dimension);
        String vectorStr = arrayToVectorString(embedding);
        Long tenantId = TenantUtils.getCurrentTenantId();
        String sql = "INSERT INTO " + tableName + " (id, chunk_id, document_id, knowledge_base_config_id, tenant_id, embedding) " +
                "VALUES (?, ?, ?, ?, ?, ?::vector)";

        pgJdbcTemplate.update(sql, id, chunkId, documentId, knowledgeBaseConfigId, tenantId, vectorStr);
    }

    @Override
    public void storeEmbeddings(List<EmbeddingRecord> records) {
        if (!isAvailable()) {
            throw new RuntimeException("PgVector数据源未配置");
        }

        for (EmbeddingRecord record : records) {
            Long tenantId = record.tenantId() != null ? record.tenantId() : TenantUtils.getCurrentTenantId();
            int dimension = record.embedding().length;
            String tableName = getTableName(dimension);
            String vectorStr = arrayToVectorString(record.embedding());
            String sql = "INSERT INTO " + tableName + " (id, chunk_id, document_id, knowledge_base_config_id, tenant_id, embedding) " +
                    "VALUES (?, ?, ?, ?, ?, ?::vector)";
            pgJdbcTemplate.update(sql, record.id(), record.chunkId(), record.documentId(),
                    record.knowledgeBaseConfigId(), tenantId, vectorStr);
        }
    }

    @Override
    public List<RetrievalResult> search(float[] queryEmbedding, Long knowledgeBaseConfigId,
                                        int limit, double scoreThreshold) {
        if (!isAvailable()) {
            throw new RuntimeException("PgVector数据源未配置");
        }

        int dimension = queryEmbedding.length;
        String tableName = getTableName(dimension);
        String vectorStr = arrayToVectorString(queryEmbedding);
        Long tenantId = TenantUtils.getCurrentTenantId();
        String sql = "SELECT chunk_id, document_id, 1 - (embedding <=> ?::vector) AS score " +
                "FROM " + tableName + " " +
                "WHERE knowledge_base_config_id = ? AND tenant_id = ? " +
                "ORDER BY embedding <=> ?::vector " +
                "LIMIT ?";

        log.info("PgVectorStore.search - dimension={}, tableName={}, knowledgeBaseConfigId={}, tenantId={}, limit={}, scoreThreshold={}",
                dimension, tableName, knowledgeBaseConfigId, tenantId, limit, scoreThreshold);

        List<Map<String, Object>> rows = pgJdbcTemplate.queryForList(sql, vectorStr, knowledgeBaseConfigId, tenantId, vectorStr, limit);

        log.info("PgVectorStore.search - SQL返回{}条记录，过滤阈值={}", rows.size(), scoreThreshold);

        List<RetrievalResult> results = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            double score = ((Number) row.get("score")).doubleValue();
            if (score >= scoreThreshold) {
                results.add(new RetrievalResult(
                        ((Number) row.get("chunk_id")).longValue(),
                        ((Number) row.get("document_id")).longValue(),
                        score
                ));
            } else {
                log.debug("PgVectorStore.search - 记录score={}低于阈值{}，被过滤", score, scoreThreshold);
            }
        }

        log.info("PgVectorStore.search - 最终返回{}条符合阈值的记录", results.size());

        return results;
    }

    @Override
    public void deleteByDocumentId(Long documentId) {
        if (!isAvailable()) return;
        for (int dim : SUPPORTED_DIMENSIONS) {
            pgJdbcTemplate.update("DELETE FROM " + getTableName(dim) + " WHERE document_id = ?", documentId);
        }
    }

    @Override
    public void deleteByKnowledgeBaseConfigId(Long knowledgeBaseConfigId) {
        if (!isAvailable()) return;
        for (int dim : SUPPORTED_DIMENSIONS) {
            pgJdbcTemplate.update("DELETE FROM " + getTableName(dim) + " WHERE knowledge_base_config_id = ?", knowledgeBaseConfigId);
        }
    }

    @Override
    public void deleteByChunkId(Long chunkId) {
        if (!isAvailable()) return;
        for (int dim : SUPPORTED_DIMENSIONS) {
            pgJdbcTemplate.update("DELETE FROM " + getTableName(dim) + " WHERE chunk_id = ?", chunkId);
        }
    }

    private String arrayToVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
