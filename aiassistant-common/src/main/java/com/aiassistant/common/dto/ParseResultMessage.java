package com.aiassistant.common.dto;

import lombok.Data;

@Data
public class ParseResultMessage {
    private Long documentId;
    private Long userId;
    private String status;
    private Integer chunkCount;
    private String errorMessage;
}
