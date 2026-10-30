package com.aiassistant.gateway.controller;

import com.aiassistant.common.dto.KnowledgeBaseDTO;
import com.aiassistant.common.result.R;
import com.aiassistant.gateway.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge-base")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @PostMapping
    public Mono<R<KnowledgeBaseDTO>> create(
            @RequestBody KnowledgeBaseDTO request,
            @RequestHeader("X-User-Id") Long userId) {
        return knowledgeBaseService.create(request.getName(), request.getDescription(), userId)
                .map(R::ok);
    }

    @GetMapping("/list")
    public Mono<R<List<KnowledgeBaseDTO>>> list(@RequestHeader("X-User-Id") Long userId) {
        return knowledgeBaseService.list(userId).map(R::ok);
    }

    @DeleteMapping("/{id}")
    public Mono<R<Void>> delete(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {
        return knowledgeBaseService.delete(id, userId).then(Mono.just(R.<Void>ok()));
    }
}
