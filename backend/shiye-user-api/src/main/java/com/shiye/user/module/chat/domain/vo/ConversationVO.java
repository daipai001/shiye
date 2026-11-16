package com.shiye.user.module.chat.domain.vo;

import com.shiye.user.module.chat.domain.entity.ConversationEntity;

import java.time.LocalDateTime;

public record ConversationVO(
        String id,
        String title,
        String scopeType,
        String scopeId,
        String model,
        String lastMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ConversationVO from(ConversationEntity entity) {
        return new ConversationVO(
                String.valueOf(entity.getId()),
                entity.getTitle(),
                entity.getScopeType(),
                entity.getScopeId() == null ? null : String.valueOf(entity.getScopeId()),
                entity.getModel(),
                entity.getLastMessage(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
