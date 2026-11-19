package com.shiye.admin.module.overview.domain.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class UsageSummaryRow {
    private Long calls;
    private Long tokens;
    private BigDecimal cost;
    private Long errors;
}
