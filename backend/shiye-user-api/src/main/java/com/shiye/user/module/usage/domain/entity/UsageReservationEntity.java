package com.shiye.user.module.usage.domain.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UsageReservationEntity {
    private Long id;
    private Long userId;
    private Long teamId;
    private String month;
    private Long userTokens;
    private BigDecimal userCost;
    private Long teamTokens;
    private BigDecimal teamCost;
    private Long globalTokens;
    private BigDecimal globalCost;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime releasedAt;
}
