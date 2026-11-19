package com.shiye.admin.module.users.domain.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AdminUserRowEntity {
    private Long id;
    private String email;
    private String displayName;
    private String status;
    private Long monthlyTokenQuota;
    private BigDecimal monthlyCostQuota;
    private Long tokensUsed;
    private BigDecimal costUsed;
    private Long articleCount;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}
