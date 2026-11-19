package com.shiye.common.settings;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemSettingsNormalizerTest {

    private final SystemSettings defaults = new SystemSettings(
            true, 200000, new BigDecimal("5.0"), 2000000, new BigDecimal("20.0"),
            0.8, 10, 0, 700, 100, 8, true, true, true);

    @Test
    void clampsInvalidValues() {
        SystemSettings parsed = new SystemSettings(
                false, -1, new BigDecimal("-1"), -1, new BigDecimal("-2"),
                1.5, 0, 50000, 99999, -1, 99, false, null, null);

        SystemSettings normalized = SystemSettingsNormalizer.normalize(defaults, parsed);

        assertFalse(normalized.registrationEnabled());
        assertEquals(200000, normalized.defaultMonthlyTokens());
        assertEquals(BigDecimal.ZERO, normalized.defaultMonthlyCost());
        assertEquals(2000000, normalized.globalMonthlyTokens());
        assertEquals(BigDecimal.ZERO, normalized.globalMonthlyCost());
        assertEquals(1.0, normalized.alertThreshold());
        assertEquals(1, normalized.chatRateLimitPerMinute());
        assertEquals(50000, normalized.dailyTokenLimit());
        assertEquals(2000, normalized.ragChunkTargetSize());
        assertEquals(100, normalized.ragChunkOverlap());
        assertEquals(20, normalized.ragTopK());
        assertFalse(normalized.ragHybridEnabled());
        assertTrue(normalized.ragRerankEnabled());
        assertTrue(normalized.ragCitationRequired());
    }

    @Test
    void allowsZeroOverlap() {
        SystemSettings parsed = new SystemSettings(
                true, 200000, new BigDecimal("5.0"), 2000000, new BigDecimal("20.0"),
                0.8, 10, 0, 700, 0, 8, true, true, true);

        SystemSettings normalized = SystemSettingsNormalizer.normalize(defaults, parsed);

        assertEquals(0, normalized.ragChunkOverlap());
    }
}
