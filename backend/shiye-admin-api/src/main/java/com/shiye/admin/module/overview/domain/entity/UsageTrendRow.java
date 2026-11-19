package com.shiye.admin.module.overview.domain.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class UsageTrendRow {
    private LocalDate day;
    private Long calls;
    private Long tokens;
    private BigDecimal cost;
}
