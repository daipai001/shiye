package com.shiye.user.module.usage.domain.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class UsageDailyRow {
    private LocalDate date;
    private Long calls;
    private Long tokens;
    private BigDecimal cost;
    private Long errors;
}
