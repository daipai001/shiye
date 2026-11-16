package com.shiye.user.module.chat.domain.vo;

import java.math.BigDecimal;

public record ChatSseUsage(
        int promptTokens,
        int completionTokens,
        int totalTokens,
        BigDecimal cost,
        String currency,
        boolean estimated
) {
}
