package com.shiye.user.module.chat.domain.entity;

import lombok.Data;

@Data
public class MessageFeedbackRow {
    private Long messageId;
    private String rating;
}
