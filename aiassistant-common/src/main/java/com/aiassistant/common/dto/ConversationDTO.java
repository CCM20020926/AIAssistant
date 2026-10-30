package com.aiassistant.common.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationDTO {
    private Long id;
    private String title;
    private String mode;
    private Long knowledgeBaseId;
    private LocalDateTime updatedAt;
}
