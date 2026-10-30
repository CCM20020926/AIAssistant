package com.aiassistant.documentparse.service;

import com.aiassistant.common.entity.Document;
import com.aiassistant.common.entity.DocumentChunk;
import com.aiassistant.documentparse.mapper.DocumentChunkMapper;
import com.aiassistant.documentparse.mapper.DocumentMapper;
import com.aiassistant.documentparse.pgmapper.ChunkVectorMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkStorageService {

    private final MinioService minioService;
    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper chunkMapper;
    private final ChunkVectorMapper vectorMapper;
    private final EmbeddingService embeddingService;

    @Transactional
    public void saveAll(Long documentId, Long knowledgeBaseId, List<TextChunkService.Chunk> chunks) {
        log.info("[doc {}] saveAll START: {} chunks to persist", documentId, chunks.size());
        int seq = 0;
        for (TextChunkService.Chunk chunk : chunks) {
            seq++;
            String objectName = String.format("chunks/%d/%d.txt", documentId, chunk.getIndex());
            byte[] content = chunk.getContent().getBytes();

            try {
                minioService.uploadFile(objectName, content, "text/plain");
                log.info("[doc {}] chunk {}/{} (index={}): MinIO upload OK -> {} ({} bytes)",
                        documentId, seq, chunks.size(), chunk.getIndex(), objectName, content.length);
            } catch (Exception e) {
                log.error("[doc {}] chunk {}/{} (index={}): MinIO upload FAILED -> {}",
                        documentId, seq, chunks.size(), chunk.getIndex(), objectName, e);
                throw new RuntimeException("MinIO upload failed", e);
            }

            DocumentChunk dc = new DocumentChunk();
            dc.setDocumentId(documentId);
            dc.setKnowledgeBaseId(knowledgeBaseId);
            dc.setChunkIndex(chunk.getIndex());
            dc.setContentPreview(chunk.getContent().length() > 500
                    ? chunk.getContent().substring(0, 500) : chunk.getContent());
            dc.setMinioPath(objectName);
            dc.setTokenCount(chunk.getContent().length());
            int mysqlRows = chunkMapper.insert(dc);
            log.info("[doc {}] chunk {}/{} (index={}): MySQL document_chunk insert rows={}",
                    documentId, seq, chunks.size(), chunk.getIndex(), mysqlRows);

            try {
                float[] embedding = embeddingService.embed(chunk.getContent());
                log.info("[doc {}] chunk {}/{} (index={}): embedding computed dims={}",
                        documentId, seq, chunks.size(), chunk.getIndex(),
                        embedding == null ? -1 : embedding.length);
                int pgRows = vectorMapper.insertChunkVector(documentId, knowledgeBaseId, chunk.getIndex(),
                        chunk.getContent(), objectName, embedding);
                log.info("[doc {}] chunk {}/{} (index={}): PG chunk_vector upsert rows={}",
                        documentId, seq, chunks.size(), chunk.getIndex(), pgRows);
            } catch (Exception e) {
                log.error("[doc {}] chunk {}/{} (index={}): embedding/PG FAILED: {}",
                        documentId, seq, chunks.size(), chunk.getIndex(), e.getMessage(), e);
                throw new RuntimeException("Embedding failed", e);
            }
        }

        Document update = new Document();
        update.setId(documentId);
        update.setParseStatus(2);
        update.setChunkCount(chunks.size());
        int rows = documentMapper.updateById(update);
        log.info("[doc {}] saveAll DONE: {} chunks persisted, document updated (parse_status=2, chunk_count={}) rows={}",
                documentId, chunks.size(), chunks.size(), rows);
    }
}
