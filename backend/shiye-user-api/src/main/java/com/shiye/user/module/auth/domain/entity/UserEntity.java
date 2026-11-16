package com.shiye.user.module.auth.domain.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UserEntity {
    private Long id;
    private String email;
    private String passwordHash;
    private String displayName;
    private String avatarUrl;
    private String status;
    private Long monthlyTokenQuota;
    private BigDecimal monthlyCostQuota;
    private String quotaSource;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
