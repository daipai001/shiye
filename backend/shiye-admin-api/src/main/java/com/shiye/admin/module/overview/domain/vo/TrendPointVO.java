package com.shiye.admin.module.overview.domain.vo;

import java.math.BigDecimal;

public record TrendPointVO(
        String date,
        long calls,
        long tokens,
        BigDecimal cost
) {
}
