package com.shiye.user.module.usage.domain.vo;

import java.math.BigDecimal;

public record RecordedUsage(
        int promptTokens,
        int completionTokens,
        int totalTokens,
        BigDecimal cost,
        String currency,
        boolean estimated
) {
}
