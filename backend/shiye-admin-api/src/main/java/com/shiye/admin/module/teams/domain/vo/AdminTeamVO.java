package com.shiye.admin.module.teams.domain.vo;

import com.shiye.admin.module.teams.domain.entity.AdminTeamRowEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminTeamVO(
        String id,
        String name,
        String ownerUserId,
        String ownerEmail,
        String status,
        long memberCount,
        long monthlyTokenQuota,
        long tokensUsed,
        BigDecimal monthlyCostQuota,
        BigDecimal costUsed,
        long calls,
        LocalDateTime createdAt
) {
    public static AdminTeamVO from(AdminTeamRowEntity row) {
        return new AdminTeamVO(
                String.valueOf(row.getId()),
                row.getName(),
                String.valueOf(row.getOwnerUserId()),
                row.getOwnerEmail(),
                row.getStatus(),
                row.getMemberCount() == null ? 0 : row.getMemberCount(),
                row.getMonthlyTokenQuota() == null ? 0 : row.getMonthlyTokenQuota(),
                row.getTokensUsed() == null ? 0 : row.getTokensUsed(),
                row.getMonthlyCostQuota() == null ? BigDecimal.ZERO : row.getMonthlyCostQuota(),
                row.getCostUsed() == null ? BigDecimal.ZERO : row.getCostUsed(),
                row.getCalls() == null ? 0 : row.getCalls(),
                row.getCreatedAt()
        );
    }
}
