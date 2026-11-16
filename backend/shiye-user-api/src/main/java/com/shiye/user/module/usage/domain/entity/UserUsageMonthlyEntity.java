package com.shiye.user.module.usage.domain.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UserUsageMonthlyEntity {
    private Long id;
    private Long userId;
    private String month;
    private Long tokens;
    private BigDecimal cost;
    private Integer calls;
    private LocalDateTime updatedAt;
}
