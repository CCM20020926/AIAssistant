package com.aiassistant.gateway.controller;

import com.aiassistant.common.dto.ParseResultMessage;
import com.aiassistant.gateway.service.DocumentNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/document")
@RequiredArgsConstructor
public class DocumentNotificationController {

    private final DocumentNotificationService notificationService;

    @GetMapping(value = "/notifications", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ParseResultMessage>> subscribe(
            @RequestHeader("X-User-Id") Long userId) {
        return notificationService.subscribe(userId);
    }
}
