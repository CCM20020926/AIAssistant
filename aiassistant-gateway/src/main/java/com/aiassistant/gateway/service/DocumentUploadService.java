package com.aiassistant.gateway.service;

import com.aiassistant.common.dto.DocumentDTO;
import com.aiassistant.common.dto.ParseTaskMessage;
import com.aiassistant.common.entity.Document;
import com.aiassistant.common.exception.BusinessException;
import com.aiassistant.common.result.ErrorCode;
import com.aiassistant.gateway.mapper.DocumentMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentUploadService {

    private final MinioClient minioClient;
    private final DocumentMapper documentMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${minio.bucketName}")
    private String bucketName;

    public Mono<DocumentDTO> upload(FilePart file, Long knowledgeBaseId, Long userId) {
        String filename = file.filename();
        String contentType = file.headers().getContentType() != null
                ? file.headers().getContentType().toString()
                : "application/octet-stream";

        return DataBufferUtils.join(file.content())
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return bytes;
                })
                .flatMap(bytes -> Mono
                        .fromCallable(() -> doUpload(bytes, filename, contentType, knowledgeBaseId, userId))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    private DocumentDTO doUpload(byte[] bytes, String filename, String contentType,
                                 Long knowledgeBaseId, Long userId) {
        try {
            String ext = getFileExtension(filename);
            String objectName = String.format("docs/%d/%d/%s.%s",
                    userId, knowledgeBaseId, UUID.randomUUID(), ext);

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .contentType(contentType)
                    .build());

            Document doc = new Document();
            doc.setKnowledgeBaseId(knowledgeBaseId);
            doc.setUserId(userId);
            doc.setName(filename);
            doc.setFileType(ext);
            doc.setFileSize((long) bytes.length);
            doc.setMinioPath(objectName);
            doc.setParseStatus(0);
            doc.setChunkCount(0);
            documentMapper.insert(doc);

            ParseTaskMessage task = new ParseTaskMessage();
            task.setDocumentId(doc.getId());
            task.setKnowledgeBaseId(knowledgeBaseId);
            task.setUserId(userId);
            task.setMinioPath(objectName);
            task.setFileName(filename);
            task.setFileType(ext);

            kafkaTemplate.send("document-parse-task", String.valueOf(doc.getId()), task);
            log.info("Sent parse task for document {}", doc.getId());

            return toDTO(doc);

        } catch (Exception e) {
            log.error("Failed to upload document", e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    public Mono<DocumentDTO> getStatus(Long documentId, Long userId) {
        return Mono.fromCallable(() -> {
            Document doc = documentMapper.selectById(documentId);
            if (doc == null || !doc.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
            }
            return toDTO(doc);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<List<DocumentDTO>> listByKnowledgeBase(Long knowledgeBaseId, Long userId) {
        return Mono.fromCallable(() -> {
            List<Document> docs = documentMapper.selectList(new QueryWrapper<Document>()
                    .eq("knowledge_base_id", knowledgeBaseId)
                    .eq("user_id", userId)
                    .orderByDesc("created_at"));
            return docs.stream().map(this::toDTO).toList();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> delete(Long documentId, Long userId) {
        return Mono.fromCallable(() -> {
            Document doc = documentMapper.selectById(documentId);
            if (doc == null || !doc.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
            }
            documentMapper.deleteById(documentId);
            try {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(doc.getMinioPath())
                        .build());
            } catch (Exception e) {
                log.warn("Failed to remove MinIO object {} for document {}", doc.getMinioPath(), documentId, e);
            }
            return true;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private DocumentDTO toDTO(Document doc) {
        DocumentDTO dto = new DocumentDTO();
        dto.setId(doc.getId());
        dto.setKnowledgeBaseId(doc.getKnowledgeBaseId());
        dto.setName(doc.getName());
        dto.setFileType(doc.getFileType());
        dto.setFileSize(doc.getFileSize());
        dto.setParseStatus(doc.getParseStatus());
        dto.setParseError(doc.getParseError());
        dto.setChunkCount(doc.getChunkCount());
        dto.setCreatedAt(doc.getCreatedAt());
        return dto;
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "bin";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
}
