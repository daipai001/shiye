package com.shiye.user.module.team.domain.vo;

import com.shiye.user.module.team.domain.entity.TeamMemberEntity;

import java.time.LocalDateTime;

public record TeamMemberVO(
        String userId,
        String email,
        String displayName,
        String role,
        String status,
        LocalDateTime joinedAt
) {
    public static TeamMemberVO from(TeamMemberEntity member) {
        return new TeamMemberVO(
                String.valueOf(member.getUserId()),
                member.getEmail(),
                member.getDisplayName(),
                member.getRole(),
                member.getStatus(),
                member.getJoinedAt()
        );
    }
}
