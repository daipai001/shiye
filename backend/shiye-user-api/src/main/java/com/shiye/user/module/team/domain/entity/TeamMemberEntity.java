package com.shiye.user.module.team.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TeamMemberEntity {
    private Long id;
    private Long teamId;
    private Long userId;
    private String role;
    private String status;
    private LocalDateTime joinedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String email;
    private String displayName;
}
