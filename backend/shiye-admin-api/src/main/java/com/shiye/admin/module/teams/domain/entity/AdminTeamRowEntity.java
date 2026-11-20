package com.shiye.admin.module.teams.domain.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AdminTeamRowEntity {
    private Long id;
    private String name;
    private Long ownerUserId;
    private String ownerEmail;
    private String status;
    private Long monthlyTokenQuota;
    private BigDecimal monthlyCostQuota;
    private Long memberCount;
    private Long tokensUsed;
    private BigDecimal costUsed;
    private Long calls;
    private LocalDateTime createdAt;
}
