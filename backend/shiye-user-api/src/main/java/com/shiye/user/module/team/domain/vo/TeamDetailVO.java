package com.shiye.user.module.team.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record TeamDetailVO(
        String id,
        String name,
        String ownerUserId,
        String myRole,
        String status,
        long memberCount,
        long monthlyTokenQuota,
        BigDecimal monthlyCostQuota,
        LocalDateTime createdAt,
        List<TeamMemberVO> members
) {
    public static TeamDetailVO from(TeamVO team, List<TeamMemberVO> members) {
        return new TeamDetailVO(
                team.id(),
                team.name(),
                team.ownerUserId(),
                team.myRole(),
                team.status(),
                team.memberCount(),
                team.monthlyTokenQuota(),
                team.monthlyCostQuota(),
                team.createdAt(),
                members
        );
    }
}
