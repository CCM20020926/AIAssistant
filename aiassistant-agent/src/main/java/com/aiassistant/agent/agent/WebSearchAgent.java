package com.aiassistant.agent.agent;

import com.aiassistant.agent.config.AgentProperties;
import com.aiassistant.agent.service.WebSearchService;
import com.aiassistant.agent.tool.WebSearchTool;
import com.aiassistant.common.dto.ChatRequest;
import com.aiassistant.common.dto.ChatStreamResponse;
import com.aiassistant.common.dto.MessageDTO;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSearchAgent {

    private static final String SYSTEM_PROMPT = """
            你是一个可联网搜索的智能助手，请用中文回答。你拥有 webSearch 工具，可检索实时网络信息。请遵循：
            1. 当用户问题涉及时事、最新数据、实时状态，或超出你知识截止时间的事实时，先调用 webSearch 检索，再依据结果作答。
            2. 调用工具时，必须结合对话上下文，把用户问题改写成语义完整、明确、可独立理解的搜索查询（补全代词、指代、主题、时间等），把改写后的查询作为 query 参数。
            3. 回答优先依据检索到的内容，并在合适处标注信息来源（网页标题或 URL）；若检索无结果，如实告知，绝不编造。
            4. 与实时信息无关的通用问题或闲聊，可直接回答，无需调用工具。
            """;

    private final ChatModel chatModel;
    private final AgentProperties agentProperties;
    private final WebSearchService webSearchService;

    public Flux<ChatStreamResponse> stream(List<MessageDTO> history, ChatRequest request) {
        WebSearchTool tool = new WebSearchTool(webSearchService);

        String model;
        if (request.isDeepThink()) {
            model = agentProperties.getModelThinking();
        } else {
            model = agentProperties.getModelDefault();
        }

        DashScopeChatOptions.DashscopeChatOptionsBuilder builder =
                DashScopeChatOptions.builder().withModel(model);
        if (request.isDeepThink()) {
            builder.withEnableThinking(true);
        }
        DashScopeChatOptions options = builder.build();

        ChatClient chatClient = ChatClient.builder(chatModel).build();

        log.debug("WebSearchAgent start: model={}, deepThink={}, historySize={}",
                model, request.isDeepThink(), history == null ? 0 : history.size());

        return chatClient.prompt()
                .messages(buildMessages(history))
                .options(options)
                .tools(tool)
                .stream()
                .chatResponse()
                .doOnSubscribe(s -> log.debug("WebSearchAgent: stream subscribed"))
                .doOnCancel(() -> log.info("WebSearchAgent: stream cancelled"))
                .doOnError(e -> log.error("WebSearchAgent: stream error", e))
                .doOnComplete(() -> log.debug("WebSearchAgent: stream completed"))
                .flatMapIterable(this::toEvents)
                .concatWith(Flux.just(ChatStreamResponse.answer("")))
                .onErrorResume(e -> {
                    log.error("WebSearchAgent stream error: {}", e.getMessage(), e);
                    return Flux.just(ChatStreamResponse.error("AI 服务暂时不可用，请稍后重试"));
                });
    }

    private List<Message> buildMessages(List<MessageDTO> history) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        if (history != null) {
            for (MessageDTO dto : history) {
                if ("user".equals(dto.getRole())) {
                    messages.add(new UserMessage(dto.getContent()));
                } else if ("assistant".equals(dto.getRole())) {
                    messages.add(new AssistantMessage(dto.getContent()));
                }
            }
        }
        return messages;
    }

    private List<ChatStreamResponse> toEvents(ChatResponse response) {
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
    }

    private boolean isThinkingContent(ChatResponse response) {
        if (response.getMetadata() != null) {
            return Boolean.TRUE.equals(response.getMetadata().get("thinking"));
        }
        return false;
    }
}
