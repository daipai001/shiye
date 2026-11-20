package com.shiye.user.module.team.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TeamFolderEntity {
    private Long id;
    private Long teamId;
    private String name;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer articleCount;
}
