package com.shiye.user.module.team.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TeamTagEntity {
    private Long id;
    private Long teamId;
    private String name;
    private String normalizedName;
    private String color;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
