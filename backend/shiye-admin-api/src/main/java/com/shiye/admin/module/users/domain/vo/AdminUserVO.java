package com.shiye.admin.module.users.domain.vo;

import com.shiye.admin.module.users.domain.entity.AdminUserRowEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminUserVO(
        String id,
        String email,
        String displayName,
        String status,
        String role,
        long monthlyTokenQuota,
        long tokensUsed,
        BigDecimal monthlyCostQuota,
        BigDecimal costUsed,
        long articleCount,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt
) {

    public static AdminUserVO from(AdminUserRowEntity row) {
        return new AdminUserVO(
                String.valueOf(row.getId()),
                row.getEmail(),
                row.getDisplayName(),
                row.getStatus(),
                "USER",
                row.getMonthlyTokenQuota() == null ? 0 : row.getMonthlyTokenQuota(),
                row.getTokensUsed() == null ? 0 : row.getTokensUsed(),
                row.getMonthlyCostQuota() == null ? BigDecimal.ZERO : row.getMonthlyCostQuota(),
                row.getCostUsed() == null ? BigDecimal.ZERO : row.getCostUsed(),
                row.getArticleCount() == null ? 0 : row.getArticleCount(),
                row.getLastLoginAt(),
                row.getCreatedAt()
        );
    }
}
