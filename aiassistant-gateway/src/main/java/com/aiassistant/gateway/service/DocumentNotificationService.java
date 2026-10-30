package com.aiassistant.gateway.service;

import com.aiassistant.common.dto.ParseResultMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class DocumentNotificationService {

    private final ConcurrentHashMap<Long, Sinks.Many<ServerSentEvent<ParseResultMessage>>> userSinks =
            new ConcurrentHashMap<>();

    public Flux<ServerSentEvent<ParseResultMessage>> subscribe(Long userId) {
        Sinks.Many<ServerSentEvent<ParseResultMessage>> sink = userSinks.computeIfAbsent(userId,
                id -> Sinks.many().multicast().onBackpressureBuffer());

        return sink.asFlux()
                .doOnCancel(() -> {
                    log.debug("SSE disconnected for user {}", userId);
                    userSinks.remove(userId, sink);
                });
    }

    public void sendNotification(ParseResultMessage message) {
        Long userId = message.getUserId();
        Sinks.Many<ServerSentEvent<ParseResultMessage>> sink = userSinks.get(userId);
        if (sink == null) {
            log.debug("No SSE connection for user {}, skipping notification for document {}",
                    userId, message.getDocumentId());
            return;
        }

        ServerSentEvent<ParseResultMessage> event = ServerSentEvent.<ParseResultMessage>builder()
                .id(String.valueOf(message.getDocumentId()))
                .event("document-parse-result")
                .data(message)
                .build();

        Sinks.EmitResult result = sink.tryEmitNext(event);
        if (result.isFailure()) {
            log.warn("Failed to emit SSE event for user {}: {}", userId, result);
        } else {
            log.info("Sent parse result notification to user {} for document {}",
                    userId, message.getDocumentId());
        }
    }
}
