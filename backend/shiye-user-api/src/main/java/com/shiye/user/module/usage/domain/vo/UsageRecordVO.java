package com.shiye.user.module.usage.domain.vo;

import com.shiye.user.module.usage.domain.entity.AiUsageEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UsageRecordVO(
        String id,
        String requestId,
        String source,
        String scene,
        String model,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        BigDecimal cost,
        String currency,
        Integer latencyMs,
        Integer firstTokenMs,
        String status,
        String errorCode,
        LocalDateTime createdAt
) {

    public static UsageRecordVO from(AiUsageEntity entity) {
        return new UsageRecordVO(
                String.valueOf(entity.getId()),
                entity.getRequestId(),
                entity.getSource(),
                entity.getScene(),
                entity.getModel(),
                value(entity.getPromptTokens()),
                value(entity.getCompletionTokens()),
                value(entity.getTotalTokens()),
                entity.getCost() == null ? BigDecimal.ZERO : entity.getCost(),
                entity.getCurrency(),
                entity.getLatencyMs(),
                entity.getFirstTokenMs(),
                entity.getStatus(),
                entity.getErrorCode(),
                entity.getCreatedAt()
        );
    }

    private static int value(Integer number) {
        return number == null ? 0 : number;
    }
}
