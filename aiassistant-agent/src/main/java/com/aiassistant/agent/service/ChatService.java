package com.aiassistant.agent.service;

import com.aiassistant.agent.agent.ChatAgent;
import com.aiassistant.common.dto.ChatRequest;
import com.aiassistant.common.dto.ChatStreamResponse;
import com.aiassistant.common.dto.MessageDTO;
import com.aiassistant.common.entity.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatAgent chatAgent;
    private final ConversationService conversationService;
    private final MessageService messageService;

    public Flux<ServerSentEvent<ChatStreamResponse>> streamChat(Long userId, ChatRequest request) {
        String conversationId = request.getConversationId();
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = conversationService.createConversation(userId, request.getContent());
        } else {
            conversationService.getById(Long.valueOf(conversationId));
        }

        final String finalConvId = conversationId;

        messageService.saveMessage(Long.valueOf(finalConvId), userId, "user", request.getContent());

        List<MessageDTO> context = messageService.getRecentMessages(Long.valueOf(finalConvId), 20);

        StringBuilder fullAssistantContent = new StringBuilder();

        return chatAgent.stream(context, request)
                .doOnSubscribe(s -> log.debug("ChatService: chatAgent.stream subscribed"))
                .doOnCancel(() -> log.warn("ChatService: chatAgent.stream subscription cancelled"))
                .doOnNext(event -> log.debug("ChatService: received event type={}", event.getType()))
                .map(event -> {
                    if ("answer".equals(event.getType()) && event.getContent() != null) {
                        fullAssistantContent.append(event.getContent());
                    }

                    ChatStreamResponse response = new ChatStreamResponse();
                    response.setType(event.getType());
                    response.setContent(event.getContent());
                    response.setMeta(event.getMeta());
                    response.setConversationId(finalConvId);

                    if ("done".equals(event.getType()) || "error".equals(event.getType())) {
                        response.setConversationId(finalConvId);
                    }

                    return ServerSentEvent.<ChatStreamResponse>builder()
                            .event(event.getType())
                            .data(response)
                            .build();
                })
                .concatWith(Flux.defer(() -> {
                    if (fullAssistantContent.length() > 0) {
                        Message saved = messageService.saveMessage(
                                Long.valueOf(finalConvId), userId, "assistant",
                                fullAssistantContent.toString());

                        conversationService.updateTitleIfNeeded(Long.valueOf(finalConvId), request.getContent());
                    }

                    ChatStreamResponse doneEvent = ChatStreamResponse.done(finalConvId, null);
                    return Flux.just(ServerSentEvent.<ChatStreamResponse>builder()
                            .event("done")
                            .data(doneEvent)
                            .build());
                }))
                .onErrorResume(e -> {
                    log.error("ChatService stream error: {}", e.getMessage(), e);
                    ChatStreamResponse errorEvent = ChatStreamResponse.error(e.getMessage());
                    return Flux.just(ServerSentEvent.<ChatStreamResponse>builder()
                            .event("error")
                            .data(errorEvent)
                            .build());
                });
    }
}
