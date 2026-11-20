package com.shiye.user.module.team.domain.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TeamEntity {
    private Long id;
    private String name;
    private Long ownerUserId;
    private String status;
    private Long monthlyTokenQuota;
    private BigDecimal monthlyCostQuota;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    private String myRole;
    private Long memberCount;
}
