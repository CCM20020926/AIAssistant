package com.aiassistant.common.dto;

import lombok.Data;

@Data
public class ParseTaskMessage {
    private Long documentId;
    private Long knowledgeBaseId;
    private Long userId;
    private String minioPath;
    private String fileName;
    private String fileType;
}
