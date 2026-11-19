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
                nonNegative(source.dailyTokenLimit(), fallback.dailyTokenLimit()),
                intOrFallback(source.ragChunkTargetSize(), fallback.ragChunkTargetSize(), 200, 2000),
                overlapOrFallback(source.ragChunkOverlap(), fallback.ragChunkOverlap(), 1000),
                intOrFallback(source.ragTopK(), fallback.ragTopK(), 1, 20),
                boolOrFallback(source.ragHybridEnabled(), fallback.ragHybridEnabled()),
                boolOrFallback(source.ragRerankEnabled(), fallback.ragRerankEnabled()),
                boolOrFallback(source.ragCitationRequired(), fallback.ragCitationRequired()),
                boolOrFallback(source.registrationInvitationRequired(),
                        fallback.registrationInvitationRequired()),
                textOrFallback(source.registrationInvitationCode(),
                        fallback.registrationInvitationCode()),
                boolOrFallback(source.alertWebhookEnabled(),
                        fallback.alertWebhookEnabled()),
                textOrFallback(source.alertWebhookUrl(), fallback.alertWebhookUrl())
        );
    }

    private static String textOrFallback(String value, String fallback) {
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        return fallback == null ? null : fallback;
    }

    private static int intOrFallback(Integer value, Integer fallback, int min, int max) {
        int effective = value == null || value <= 0
                ? (fallback == null ? min : fallback) : value;
        return (int) clamp(effective, min, max);
    }

    private static int overlapOrFallback(Integer value, Integer fallback, int max) {
        int effective;
        if (value == null || value < 0) {
            effective = fallback == null ? 0 : fallback;
        } else {
            effective = value;
        }
        return (int) clamp(effective, 0, max);
    }

    private static boolean boolOrFallback(Boolean value, Boolean fallback) {
        if (value != null) {
            return value;
        }
        return fallback != null && fallback;
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
