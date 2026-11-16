package com.shiye.user.module.usage.domain.vo;

import java.math.BigDecimal;

public record UsageTotals(
        long tokens,
        BigDecimal cost,
        long calls
) {
}
