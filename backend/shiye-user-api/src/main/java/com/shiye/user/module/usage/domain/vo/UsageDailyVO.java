package com.shiye.user.module.usage.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UsageDailyVO(
        LocalDate date,
        long calls,
        long tokens,
        BigDecimal cost,
        long errors
) {
}
