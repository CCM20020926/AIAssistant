package com.aiassistant.documentparse.service;

import com.aiassistant.common.dto.ParseResultMessage;
import com.aiassistant.common.dto.ParseTaskMessage;
import com.aiassistant.common.entity.Document;
import com.aiassistant.documentparse.mapper.DocumentMapper;
import com.aiassistant.documentparse.producer.ParseResultProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentParseService {

    private final MinerUService minerUService;
    private final TextChunkService textChunkService;
    private final ChunkStorageService chunkStorageService;
    private final MinioService minioService;
    private final DocumentMapper documentMapper;
    private final ParseResultProducer resultProducer;

    public void execute(ParseTaskMessage task) {
        Long documentId = task.getDocumentId();
        String step = "0-INIT";
        log.info("[doc {}] ===== parse pipeline START =====", documentId);

        try {
            step = "1-MARK_PARSING";
            Document update = new Document();
            update.setId(documentId);
            update.setParseStatus(1);
            documentMapper.updateById(update);
            log.info("[doc {}] STEP {} done (parse_status=1)", documentId, step);

            step = "2-DOWNLOAD_MINIO";
            log.info("[doc {}] STEP {} start: object={}", documentId, step, task.getMinioPath());
            byte[] fileContent = minioService.downloadFile(task.getMinioPath());
            log.info("[doc {}] STEP {} done: downloaded {} bytes", documentId, step,
                    fileContent == null ? 0 : fileContent.length);

            step = "3-MINERU_PARSE";
            log.info("[doc {}] STEP {} start: file={}", documentId, step, task.getFileName());
            MinerUService.ParseResult parseResult = minerUService.parse(fileContent, task.getFileName());
            String markdown = parseResult.getMarkdown();
            log.info("[doc {}] STEP {} done: markdown length={}", documentId, step,
                    markdown == null ? -1 : markdown.length());

            step = "4-CHUNK";
            List<TextChunkService.Chunk> chunks = textChunkService.chunk(markdown);
            log.info("[doc {}] STEP {} done: chunk count={}", documentId, step, chunks.size());

            step = "5-SAVE_ALL";
            log.info("[doc {}] STEP {} start: persisting {} chunks (MinIO + MySQL document_chunk + embedding + PG chunk_vector)",
                    documentId, step, chunks.size());
            chunkStorageService.saveAll(documentId, task.getKnowledgeBaseId(), chunks);
            log.info("[doc {}] STEP {} done", documentId, step);

            step = "6-SEND_SUCCESS";
            resultProducer.sendSuccess(documentId, task.getUserId(), chunks.size());
            log.info("[doc {}] STEP {} done: parsed successfully with {} chunks", documentId, step, chunks.size());
            log.info("[doc {}] ===== parse pipeline SUCCESS =====", documentId);

        } catch (Exception e) {
            log.error("[doc {}] ===== parse pipeline FAILED at STEP {} ===== reason: {}",
                    documentId, step, e.getMessage(), e);

            Document update = new Document();
            update.setId(documentId);
            update.setParseStatus(3);
            update.setParseError(e.getMessage());
            documentMapper.updateById(update);

            resultProducer.sendFailure(documentId, task.getUserId(), e.getMessage());
        }
    }
}
