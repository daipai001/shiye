package com.shiye.user.module.chat.domain.vo;

public record ChatSseMeta(
        String requestId,
        String conversationId,
        String messageId
) {
}
