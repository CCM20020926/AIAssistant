package com.aiassistant.agent.agent;

import com.aiassistant.agent.config.AgentProperties;
import com.aiassistant.common.dto.ChatRequest;
import com.aiassistant.common.dto.ChatStreamResponse;
import com.aiassistant.common.dto.MessageDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatAgent {

    private final ChatModel chatModel;
    private final AgentProperties agentProperties;

    public Flux<ChatStreamResponse> stream(List<MessageDTO> history, ChatRequest request) {
        List<Message> messages = buildMessages(history);
        log.debug("chatModel class: {}", chatModel.getClass().getName());

        String model;
        if (request.isDeepThink()) {
            model = agentProperties.getModelThinking();
        } else {
            model = agentProperties.getModelDefault();
        }

        DashScopeChatOptions.DashscopeChatOptionsBuilder builder = DashScopeChatOptions.builder()
                .withModel(model);


        if(request.isDeepThink()){
            builder.withEnableThinking(true);
        }

        DashScopeChatOptions options = builder.build();

        log.debug("ChatAgent using model: {}, deepThink: {}", model, request.isDeepThink());

        Prompt prompt = new Prompt(messages, options);

        return chatModel.stream(prompt)
                .doOnSubscribe(s -> log.debug("ChatAgent: chatModel.stream subscribed"))
                .doOnCancel(() -> log.info("ChatAgent: chatModel.stream subscription cancelled"))
                .doOnError(e -> log.error("ChatAgent: chatModel.stream error", e))
                .doOnComplete(() -> log.debug("ChatAgent: chatModel.stream completed"))
                .flatMapIterable(response -> {
                    List<ChatStreamResponse> events = new ArrayList<>();

                    if (response.getResult() != null && response.getResult().getOutput() != null) {
                        String content = response.getResult().getOutput().getText();
                        if (content != null && !content.isEmpty()) {

                            if (isThinkingContent(response)) {
                                events.add(ChatStreamResponse.thinking(content));
                            } else {
                                events.add(ChatStreamResponse.answer(content));
                            }
                        }
                    }

                    return events;
                })
                .concatWith(Flux.defer(() -> {
                    return Flux.just(ChatStreamResponse.answer(""));
                }))
                .onErrorResume(e -> {
                    log.error("ChatAgent stream error: {}", e.getMessage(), e);
                    return Flux.just(ChatStreamResponse.error("AI 服务暂时不可用，请稍后重试"));
                });
    }

    private List<Message> buildMessages(List<MessageDTO> history) {
        List<Message> messages = new ArrayList<>();

        messages.add(new SystemMessage("你是一个智能助手，请用中文回答用户的问题。"));

        for (MessageDTO dto : history) {
            if ("user".equals(dto.getRole())) {
                messages.add(new UserMessage(dto.getContent()));
            } else if ("assistant".equals(dto.getRole())) {
                messages.add(new AssistantMessage(dto.getContent()));
            }
        }

        return messages;
    }

    private boolean isThinkingContent(ChatResponse response) {
        if (response.getMetadata() != null) {
            Object thinking = response.getMetadata().get("thinking");
            return Boolean.TRUE.equals(thinking);
        }
        return false;
    }
}
