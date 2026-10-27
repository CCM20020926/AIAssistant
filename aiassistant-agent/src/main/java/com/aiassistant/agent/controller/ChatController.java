package com.aiassistant.agent.controller;

import com.aiassistant.common.dto.ChatRequest;
import com.aiassistant.common.dto.ChatStreamResponse;
import com.aiassistant.agent.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatStreamResponse>> chatStream(
            @RequestBody ChatRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        return chatService.streamChat(userId, request);
    }
}
