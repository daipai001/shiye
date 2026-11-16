package com.shiye.user.module.chat.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageEntity {
    private Long id;
    private Long conversationId;
    private Long userId;
    private String role;
    private String content;
    private String model;
    private Integer promptTokens;
    private Integer completionTokens;
    private String requestId;
    private String status;
    private String errorCode;
    private Integer firstTokenMs;
    private Integer latencyMs;
    private LocalDateTime createdAt;
}
