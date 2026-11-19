package com.shiye.user.module.usage.domain.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class UsageBreakdownRow {
    private String label;
    private Long calls;
    private Long tokens;
    private BigDecimal cost;
}
