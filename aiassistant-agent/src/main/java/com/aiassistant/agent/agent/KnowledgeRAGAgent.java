package com.aiassistant.agent.agent;

import com.aiassistant.agent.config.AgentProperties;
import com.aiassistant.agent.service.KnowledgeRetrievalService;
import com.aiassistant.agent.tool.KnowledgeRetrievalTool;
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
public class KnowledgeRAGAgent {

    private static final String SYSTEM_PROMPT = """
            你是一个基于知识库的智能助手，请用中文回答。你拥有 retrieveKnowledge 工具，可从知识库检索文档内容。请遵循：
            1. 当用户问题需要事实性资料、或答案可能来自知识库文档时，先调用 retrieveKnowledge 检索，再依据检索结果作答。
            2. 调用工具时，必须结合对话上下文，把用户问题改写成语义完整、明确、可独立理解的查询（补全代词、指代、主题、时间等），把改写后的查询作为 query 参数。
            3. 若检索无结果，如实告知用户知识库中没有相关资料，绝不编造。
            4. 回答优先依据检索到的内容；必要时说明信息来自知识库。
            5. 与知识库无关的闲聊或通用问题，可直接回答，无需调用工具。
            """;

    private final ChatModel chatModel;
    private final AgentProperties agentProperties;
    private final KnowledgeRetrievalService retrievalService;

    public Flux<ChatStreamResponse> stream(List<MessageDTO> history, ChatRequest request) {
        Long knowledgeBaseId = Long.valueOf(request.getKnowledgeBaseId());
        KnowledgeRetrievalTool tool = new KnowledgeRetrievalTool(knowledgeBaseId, retrievalService);

        String model = request.isDeepThink()
                ? agentProperties.getModelThinking()
                : agentProperties.getModelDefault();
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withModel(model)
                .build();

        ChatClient chatClient = ChatClient.builder(chatModel).build();

        log.debug("KnowledgeRAGAgent start: model={}, kbId={}, deepThink={}, historySize={}",
                model, knowledgeBaseId, request.isDeepThink(), history == null ? 0 : history.size());

        return chatClient.prompt()
                .messages(buildMessages(history))
                .options(options)
                .tools(tool)
                .stream()
                .chatResponse()
                .doOnSubscribe(s -> log.debug("KnowledgeRAGAgent: stream subscribed"))
                .doOnCancel(() -> log.info("KnowledgeRAGAgent: stream cancelled"))
                .doOnError(e -> log.error("KnowledgeRAGAgent: stream error", e))
                .doOnComplete(() -> log.debug("KnowledgeRAGAgent: stream completed"))
                .flatMapIterable(this::toEvents)
                .concatWith(Flux.just(ChatStreamResponse.answer("")))
                .onErrorResume(e -> {
                    log.error("KnowledgeRAGAgent stream error: {}", e.getMessage(), e);
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
