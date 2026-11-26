package com.shiye.user.module.usage.domain.vo;

import java.math.BigDecimal;

public record GlobalReservation(
        String month,
        long tokens,
        BigDecimal cost
) {
}
