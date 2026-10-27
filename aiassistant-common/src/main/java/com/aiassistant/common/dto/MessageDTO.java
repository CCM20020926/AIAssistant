package com.aiassistant.common.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageDTO {
    private Long id;
    private String role;
    private String content;
    private String meta;
    private Integer seqNo;
    private LocalDateTime createdAt;
}
