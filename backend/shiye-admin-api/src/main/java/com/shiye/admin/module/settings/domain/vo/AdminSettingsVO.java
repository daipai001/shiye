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
        String source
) {
}
