package com.shiye.user.module.usage.domain.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TeamUsageMonthlyEntity {
    private Long id;
    private Long teamId;
    private String month;
    private Long tokens;
    private BigDecimal cost;
    private Long calls;
    private LocalDateTime updatedAt;
}
