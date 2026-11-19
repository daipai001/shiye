package com.shiye.admin.module.usage.domain.vo;

import com.shiye.admin.module.usage.domain.entity.AdminUsageRowEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminUsageRecordVO(
        String id,
        String requestId,
        String userId,
        String userEmail,
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

    public static AdminUsageRecordVO from(AdminUsageRowEntity entity) {
        return new AdminUsageRecordVO(
                String.valueOf(entity.getId()),
                entity.getRequestId(),
                String.valueOf(entity.getUserId()),
                entity.getUserEmail(),
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
