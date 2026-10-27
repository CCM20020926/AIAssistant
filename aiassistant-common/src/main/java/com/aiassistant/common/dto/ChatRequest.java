package com.aiassistant.common.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String conversationId;
    private String content;
    private boolean deepThink;
    private boolean webSearch;
    private boolean pptMode;
    private boolean knowledgeMode;
    private String knowledgeBaseId;
}
