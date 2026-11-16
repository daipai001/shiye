package com.shiye.user.module.chat.domain.vo;

import com.shiye.user.module.chat.domain.entity.ConversationEntity;

public record ConversationDetailVO(
        String id,
        String title,
        String scopeType,
        String scopeId,
        String model,
        long messageCount
) {

    public static ConversationDetailVO from(ConversationEntity entity, long messageCount) {
        return new ConversationDetailVO(
                String.valueOf(entity.getId()),
                entity.getTitle(),
                entity.getScopeType(),
                entity.getScopeId() == null ? null : String.valueOf(entity.getScopeId()),
                entity.getModel(),
                messageCount
        );
    }
}
