package com.hxh.apboa.engine.rag.knowledge;

import com.hxh.apboa.common.entity.KnowledgeBaseConfig;
import com.hxh.apboa.common.vo.RagDocumentChunkVO;
import com.hxh.apboa.knowledge.service.KnowledgeBaseConfigService;
import com.hxh.apboa.engine.rag.service.LocalRagService;
import com.hxh.apboa.common.util.TenantUtils;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.DocumentMetadata;
import io.agentscope.core.rag.model.RetrieveConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地RAG知识库实现，实现agentscope的Knowledge接口
 *
 * @author huxuehao
 */
public class LocalKnowledge implements Knowledge {

    private static final Logger log = LoggerFactory.getLogger(LocalKnowledge.class);

    private final Long knowledgeBaseConfigId;
    private final Long tenantId;
    private final String tenantCode;
    private final LocalRagService localRagService;
    private final KnowledgeBaseConfigService knowledgeBaseConfigService;

    public LocalKnowledge(Long knowledgeBaseConfigId,
                          LocalRagService localRagService,
                          KnowledgeBaseConfigService knowledgeBaseConfigService) {
        this.knowledgeBaseConfigId = knowledgeBaseConfigId;
        this.localRagService = localRagService;
        this.knowledgeBaseConfigService = knowledgeBaseConfigService;
        // Capture tenant from current thread (set during agent construction)
        Long currentTenantId = TenantUtils.getCurrentTenantId();
        this.tenantId = currentTenantId != null ? currentTenantId : 1L;
        String currentTenantCode = TenantUtils.getCurrentTenantCode();
        this.tenantCode = currentTenantCode != null ? currentTenantCode : "default";
    }

    @Override
    public Mono<Void> addDocuments(List<Document> documents) {
        return Mono.fromRunnable(() -> {
            log.info("LocalKnowledge.addDocuments 被调用, 文档数量={}", documents.size());
        });
    }

    @Override
    public Mono<List<Document>> retrieve(String query, RetrieveConfig config) {
        return Mono.fromSupplier(() -> {
            TenantUtils.setCurrentTenant(tenantId, tenantCode);
            try {
                KnowledgeBaseConfig kbConfig = knowledgeBaseConfigService.getById(knowledgeBaseConfigId);
                if (kbConfig == null || !kbConfig.getEnabled()) {
                    log.warn("知识库配置不存在或已禁用, id={}", knowledgeBaseConfigId);
                    return new ArrayList<>();
                }

                int limit = config != null ? config.getLimit() : 5;
                double scoreThreshold = config != null ? config.getScoreThreshold() : 0.5;

                log.info("LocalKnowledge.retrieve - query={}, knowledgeBaseConfigId={}, tenantId={}, tenantCode={}, limit={}, scoreThreshold={}",
                        query, knowledgeBaseConfigId, tenantId, tenantCode, limit, scoreThreshold);

                List<RagDocumentChunkVO> chunks = localRagService.retrieve(
                        query, kbConfig, limit, scoreThreshold);

                log.info("LocalKnowledge.retrieve - LocalRagService返回{}条分块记录", chunks.size());

                List<Document> documents = new ArrayList<>();
                for (RagDocumentChunkVO chunk : chunks) {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("documentId", chunk.getDocumentId());
                    payload.put("chunkIndex", chunk.getChunkIndex());
                    payload.put("tokenCount", chunk.getTokenCount());
                    payload.put("score", chunk.getScore());

                    DocumentMetadata metadata = DocumentMetadata.builder()
                            .content(TextBlock.builder().text(chunk.getContent()).build())
                            .docId(String.valueOf(chunk.getDocumentId()))
                            .chunkId(String.valueOf(chunk.getId()))
                            .payload(payload)
                            .build();

                    Document doc = new Document(metadata);
                    documents.add(doc);
                }

                log.info("本地RAG检索完成, query={}, results={}", query, documents.size());
                return documents;
            } catch (Exception e) {
                log.error("本地RAG检索失败, query={}, knowledgeBaseConfigId={}, tenantId={}", query, knowledgeBaseConfigId, tenantId, e);
                return new ArrayList<Document>();
            } finally {
                TenantUtils.clear();
            }
        });
    }
}
