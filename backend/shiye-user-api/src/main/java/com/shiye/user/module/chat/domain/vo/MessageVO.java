package com.shiye.user.module.chat.domain.vo;

import com.shiye.user.module.chat.domain.entity.MessageEntity;

import java.time.LocalDateTime;
import java.util.List;

public record MessageVO(
        String id,
        String role,
        String content,
        String model,
        String status,
        int promptTokens,
        int completionTokens,
        Integer firstTokenMs,
        Integer latencyMs,
        List<CitationVO> citations,
        LocalDateTime createdAt
) {

    public static MessageVO from(MessageEntity entity, List<CitationVO> citations) {
        return new MessageVO(
                String.valueOf(entity.getId()),
                entity.getRole(),
                entity.getContent(),
                entity.getModel(),
                entity.getStatus(),
                entity.getPromptTokens() == null ? 0 : entity.getPromptTokens(),
                entity.getCompletionTokens() == null ? 0 : entity.getCompletionTokens(),
                entity.getFirstTokenMs(),
                entity.getLatencyMs(),
                citations,
                entity.getCreatedAt()
        );
    }
}
