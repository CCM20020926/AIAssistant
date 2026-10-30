package com.aiassistant.documentparse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public float[] embed(String text) {
        try {
            float[] vector = embeddingModel.embed(text);
            log.debug("Embedding OK: textLen={}, dims={}",
                    text == null ? 0 : text.length(), vector == null ? -1 : vector.length);
            return vector;
        } catch (Exception e) {
            log.error("Embedding FAILED: textLen={}, err={}",
                    text == null ? 0 : text.length(), e.getMessage(), e);
            throw e;
        }
    }
}
