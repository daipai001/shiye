package com.shiye.user.module.usage.domain.vo;

import java.math.BigDecimal;

public record UsageBreakdownVO(
        String label,
        long calls,
        long tokens,
        BigDecimal cost
) {
}
