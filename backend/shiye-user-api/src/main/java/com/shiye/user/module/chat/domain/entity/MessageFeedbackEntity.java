package com.shiye.user.module.chat.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageFeedbackEntity {
    private Long id;
    private Long messageId;
    private Long userId;
    private String rating;
    private String comment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
