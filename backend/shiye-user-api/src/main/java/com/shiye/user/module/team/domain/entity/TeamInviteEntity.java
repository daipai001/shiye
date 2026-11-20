package com.shiye.user.module.team.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TeamInviteEntity {
    private Long id;
    private Long teamId;
    private Long inviterUserId;
    private String tokenHash;
    private String role;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private LocalDateTime createdAt;
}
