package com.shiye.common.settings;

import java.math.BigDecimal;

/**
 * 系统运行设置：注册开关、默认配额、全局预算和限流。数据库覆盖环境默认值。
 */
public record SystemSettings(
        boolean registrationEnabled,
        long defaultMonthlyTokens,
        BigDecimal defaultMonthlyCost,
        long globalMonthlyTokens,
        BigDecimal globalMonthlyCost,
        double alertThreshold,
        int chatRateLimitPerMinute,
        long dailyTokenLimit,
        Integer ragChunkTargetSize,
        Integer ragChunkOverlap,
        Integer ragTopK,
        Boolean ragHybridEnabled,
        Boolean ragRerankEnabled,
        Boolean ragCitationRequired
) {
}
