package com.shiye.user.module.chat.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationEntity {
    private Long id;
    private Long userId;
    private String title;
    private String scopeType;
    private Long scopeId;
    private String model;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    private String lastMessage;
    private Integer messageCount;
}
