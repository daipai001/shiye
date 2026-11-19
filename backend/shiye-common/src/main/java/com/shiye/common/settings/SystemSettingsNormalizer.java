package com.shiye.common.settings;

import java.math.BigDecimal;

public final class SystemSettingsNormalizer {

    private SystemSettingsNormalizer() {
    }

    public static SystemSettings normalize(SystemSettings defaults, SystemSettings parsed) {
        SystemSettings source = parsed == null ? defaults : parsed;
        SystemSettings fallback = defaults;
        return new SystemSettings(
                source.registrationEnabled(),
                nonNegative(source.defaultMonthlyTokens(), fallback.defaultMonthlyTokens()),
                money(source.defaultMonthlyCost(), fallback.defaultMonthlyCost()),
                nonNegative(source.globalMonthlyTokens(), fallback.globalMonthlyTokens()),
                money(source.globalMonthlyCost(), fallback.globalMonthlyCost()),
                clamp(source.alertThreshold(), 0, 1),
                (int) clamp(source.chatRateLimitPerMinute(), 1, 1000),
                nonNegative(source.dailyTokenLimit(), fallback.dailyTokenLimit())
        );
    }

    private static long nonNegative(long value, long fallback) {
        if (value < 0) {
            return Math.max(0, fallback);
        }
        return value;
    }

    private static BigDecimal money(BigDecimal value, BigDecimal fallback) {
        BigDecimal effective = value == null ? fallback : value;
        return effective == null || effective.signum() < 0 ? BigDecimal.ZERO : effective;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(value, max));
    }
}
