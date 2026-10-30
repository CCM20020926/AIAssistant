package com.aiassistant.common.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DocumentDTO {
    private Long id;
    private Long knowledgeBaseId;
    private String name;
    private String fileType;
    private Long fileSize;
    private Integer parseStatus;
    private String parseError;
    private Integer chunkCount;
    private LocalDateTime createdAt;
}
