package com.aiassistant.common.dto;

import lombok.Data;

@Data
public class ChatStreamResponse {
    private String type;
    private String content;
    private Object meta;
    private String messageId;
    private String conversationId;

    public static ChatStreamResponse thinking(String content) {
        ChatStreamResponse r = new ChatStreamResponse();
        r.setType("thinking");
        r.setContent(content);
        return r;
    }

    public static ChatStreamResponse answer(String content) {
        ChatStreamResponse r = new ChatStreamResponse();
        r.setType("answer");
        r.setContent(content);
        return r;
    }

    public static ChatStreamResponse done(String conversationId, String messageId) {
        ChatStreamResponse r = new ChatStreamResponse();
        r.setType("done");
        r.setConversationId(conversationId);
        r.setMessageId(messageId);
        return r;
    }

    public static ChatStreamResponse error(String message) {
        ChatStreamResponse r = new ChatStreamResponse();
        r.setType("error");
        r.setContent(message);
        return r;
    }
}
