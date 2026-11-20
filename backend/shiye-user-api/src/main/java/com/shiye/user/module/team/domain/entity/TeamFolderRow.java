package com.shiye.user.module.team.domain.entity;

import lombok.Data;

@Data
public class TeamFolderRow {
    private Long articleId;
    private Long folderId;
    private String name;
    private Integer articleCount;
}
