package com.aiassistant.common.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeBaseDTO {
    private Long id;
    private String name;
    private String description;
    private Integer docCount;
    private LocalDateTime createdAt;
}
