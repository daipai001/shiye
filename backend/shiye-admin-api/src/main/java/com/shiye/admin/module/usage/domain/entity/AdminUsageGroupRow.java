package com.shiye.admin.module.usage.domain.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AdminUsageGroupRow {
    private String key;
    private Long calls;
    private Long tokens;
    private BigDecimal cost;
}
