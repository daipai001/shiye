package com.shiye.user.module.usage.domain.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class UsageRangeTotalsRow {
    private Long calls;
    private Long tokens;
    private BigDecimal cost;
    private Long errors;
}
