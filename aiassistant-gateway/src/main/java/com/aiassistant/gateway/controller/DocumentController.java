package com.aiassistant.gateway.controller;

import com.aiassistant.common.dto.DocumentDTO;
import com.aiassistant.common.result.R;
import com.aiassistant.gateway.service.DocumentUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/document")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentUploadService uploadService;

    @PostMapping("/upload")
    public Mono<R<DocumentDTO>> upload(
            @RequestPart("file") FilePart file,
            @RequestParam Long knowledgeBaseId,
            @RequestHeader("X-User-Id") Long userId) {
        return uploadService.upload(file, knowledgeBaseId, userId).map(R::ok);
    }

    @GetMapping("/status/{documentId}")
    public Mono<R<DocumentDTO>> getStatus(
            @PathVariable Long documentId,
            @RequestHeader("X-User-Id") Long userId) {
        return uploadService.getStatus(documentId, userId).map(R::ok);
    }

    @GetMapping("/list")
    public Mono<R<List<DocumentDTO>>> list(
            @RequestParam Long knowledgeBaseId,
            @RequestHeader("X-User-Id") Long userId) {
        return uploadService.listByKnowledgeBase(knowledgeBaseId, userId).map(R::ok);
    }

    @DeleteMapping("/{documentId}")
    public Mono<R<Void>> delete(
            @PathVariable Long documentId,
            @RequestHeader("X-User-Id") Long userId) {
        return uploadService.delete(documentId, userId).then(Mono.just(R.<Void>ok()));
    }
}
