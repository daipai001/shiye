package com.shiye.user.module.team.domain.vo;

import com.shiye.user.module.team.domain.entity.TeamEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TeamVO(
        String id,
        String name,
        String ownerUserId,
        String myRole,
        String status,
        long memberCount,
        long monthlyTokenQuota,
        BigDecimal monthlyCostQuota,
        LocalDateTime createdAt
) {
    public static TeamVO from(TeamEntity team) {
        return new TeamVO(
                String.valueOf(team.getId()),
                team.getName(),
                String.valueOf(team.getOwnerUserId()),
                team.getMyRole(),
                team.getStatus(),
                team.getMemberCount() == null ? 0 : team.getMemberCount(),
                team.getMonthlyTokenQuota() == null ? 0 : team.getMonthlyTokenQuota(),
                team.getMonthlyCostQuota() == null ? BigDecimal.ZERO : team.getMonthlyCostQuota(),
                team.getCreatedAt()
        );
    }
}
