package com.aiassistant.agent.entity;

public record RetrievedChunk(Long documentId,
                             int chunkIndex,
                             String content,
                             String minioPath,
                             double score) {
}
