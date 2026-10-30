package com.aiassistant.documentparse.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class TextChunkService {

    private static final int CHUNK_SIZE = 500;
    private static final int CHUNK_OVERLAP = 50;
    private static final List<String> SEPARATORS = List.of(
            "\n\n", "\n", "。", ".", "！", "!", "？", "?",
            "；", ";", "，", ",", " ", ""
    );

    public List<Chunk> chunk(String text) {
        List<Chunk> chunks = new ArrayList<>();
        log.info("Chunking input: text length={}", text == null ? -1 : text.length());
        if (text == null || text.isBlank()) {
            log.warn("Chunking aborted: input text is null or blank -> 0 chunks");
            return chunks;
        }

        List<String> segments = recursiveSplit(text, 0);
        log.info("Chunking: recursiveSplit produced {} segments", segments.size());

        StringBuilder currentChunk = new StringBuilder();
        int index = 0;

        for (String segment : segments) {
            if (currentChunk.length() + segment.length() > CHUNK_SIZE && currentChunk.length() > 0) {
                Chunk chunk = new Chunk();
                chunk.setIndex(index++);
                chunk.setContent(currentChunk.toString());
                chunks.add(chunk);

                String overlap = getOverlap(currentChunk.toString());
                currentChunk = new StringBuilder(overlap);
            }
            currentChunk.append(segment);
        }

        if (currentChunk.length() > 0) {
            Chunk chunk = new Chunk();
            chunk.setIndex(index);
            chunk.setContent(currentChunk.toString());
            chunks.add(chunk);
        }

        log.info("Text chunked into {} segments", chunks.size());
        return chunks;
    }

    private List<String> recursiveSplit(String text, int separatorIndex) {
        List<String> result = new ArrayList<>();
        if (separatorIndex >= SEPARATORS.size()) {
            result.add(text);
            return result;
        }

        String separator = SEPARATORS.get(separatorIndex);
        if (separator.isEmpty()) {
            for (char c : text.toCharArray()) {
                result.add(String.valueOf(c));
            }
            return result;
        }

        String[] parts = text.split(java.util.regex.Pattern.quote(separator), -1);
        for (String part : parts) {
            if (part.length() > CHUNK_SIZE) {
                result.addAll(recursiveSplit(part, separatorIndex + 1));
            } else if (!part.isEmpty()) {
                result.add(part + separator);
            }
        }
        return result;
    }

    private String getOverlap(String text) {
        if (text.length() <= CHUNK_OVERLAP) {
            return text;
        }
        return text.substring(text.length() - CHUNK_OVERLAP);
    }

    @Data
    public static class Chunk {
        private int index;
        private String content;
    }
}
