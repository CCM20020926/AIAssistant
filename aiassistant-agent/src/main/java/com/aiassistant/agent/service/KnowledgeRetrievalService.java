package com.aiassistant.agent.service;

import com.aiassistant.agent.entity.RetrievedChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class KnowledgeRetrievalService {

    private static final String SEARCH_SQL =
            "SELECT document_id, chunk_index, content, minio_path, " +
            "       1 - (embedding <=> ?::vector) AS score " +
            "FROM chunk_vector " +
            "WHERE knowledge_base_id = ? " +
            "ORDER BY embedding <=> ?::vector " +
            "LIMIT ?";

    private final EmbeddingModel embeddingModel;
    private final JdbcTemplate pgJdbcTemplate;

    public KnowledgeRetrievalService(EmbeddingModel embeddingModel,
                                     @Qualifier("pgJdbcTemplate") JdbcTemplate pgJdbcTemplate) {
        this.embeddingModel = embeddingModel;
        this.pgJdbcTemplate = pgJdbcTemplate;
    }

    public List<RetrievedChunk> retrieve(Long knowledgeBaseId, String query, int topK) {
        if (knowledgeBaseId == null || query == null || query.isBlank()) {
            log.debug("RAG retrieve skipped: kbId={}, queryBlank={}", knowledgeBaseId, query == null || query.isBlank());
            return List.of();
        }

        log.debug("RAG retrieve start: kbId={}, topK={}, query={}", knowledgeBaseId, topK, query);

        float[] queryVector = embeddingModel.embed(query);
        String literal = toVectorLiteral(queryVector);
        log.debug("RAG query embedded: dims={}", queryVector == null ? -1 : queryVector.length);

        List<RetrievedChunk> chunks = pgJdbcTemplate.query(
                SEARCH_SQL,
                (rs, rowNum) -> new RetrievedChunk(
                        rs.getLong("document_id"),
                        rs.getInt("chunk_index"),
                        rs.getString("content"),
                        rs.getString("minio_path"),
                        rs.getDouble("score")),
                literal, knowledgeBaseId, literal, topK);

        log.debug("RAG retrieve done: {} chunks matched for kbId={}", chunks.size(), knowledgeBaseId);
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk c = chunks.get(i);
            log.debug("RAG chunk[{}] documentId={}, chunkIndex={}, score={}, contentLen={}, content={}",
                    i + 1, c.documentId(), c.chunkIndex(), String.format("%.4f", c.score()),
                    c.content() == null ? 0 : c.content().length(), c.content());
        }
        return chunks;
    }

    private String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
