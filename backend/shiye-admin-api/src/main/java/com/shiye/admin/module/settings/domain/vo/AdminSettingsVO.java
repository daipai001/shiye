package com.shiye.admin.module.settings.domain.vo;

import java.math.BigDecimal;

public record AdminSettingsVO(
        boolean registrationEnabled,
        long defaultMonthlyTokens,
        BigDecimal defaultMonthlyCost,
        long globalMonthlyTokens,
        BigDecimal globalMonthlyCost,
        double alertThreshold,
        int chatRateLimitPerMinute,
        long dailyTokenLimit,
        int ragChunkTargetSize,
        int ragChunkOverlap,
        int ragTopK,
        boolean ragHybridEnabled,
        boolean ragRerankEnabled,
        boolean ragCitationRequired,
        boolean registrationInvitationRequired,
        String registrationInvitationCode,
        boolean alertWebhookEnabled,
        String alertWebhookUrl,
        String source
) {
}
