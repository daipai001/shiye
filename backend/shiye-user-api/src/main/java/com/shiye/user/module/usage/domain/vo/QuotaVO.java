package com.shiye.user.module.usage.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record QuotaVO(
        String month,
        long tokenQuota,
        long tokensUsed,
        long tokenRemaining,
        BigDecimal costQuota,
        BigDecimal costUsed,
        BigDecimal costRemaining,
        LocalDateTime resetAt
) {
}
