package com.shiye.admin.module.settings.domain.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AdminSettingsUpdateRequest(
        boolean registrationEnabled,
        @Min(0) long defaultMonthlyTokens,
        @NotNull @DecimalMin("0.0") BigDecimal defaultMonthlyCost,
        @Min(0) long globalMonthlyTokens,
        @NotNull @DecimalMin("0.0") BigDecimal globalMonthlyCost,
        @DecimalMin("0.0") @DecimalMax("1.0") double alertThreshold,
        @Min(1) @Max(1000) int chatRateLimitPerMinute,
        @Min(0) long dailyTokenLimit,
        @Min(200) @Max(2000) int ragChunkTargetSize,
        @Min(0) @Max(1000) int ragChunkOverlap,
        @Min(1) @Max(20) int ragTopK,
        boolean ragHybridEnabled,
        boolean ragRerankEnabled,
        boolean ragCitationRequired,
        boolean registrationInvitationRequired,
        String registrationInvitationCode
) {
}
