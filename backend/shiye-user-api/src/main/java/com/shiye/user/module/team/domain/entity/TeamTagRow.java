package com.shiye.user.module.team.domain.entity;

import lombok.Data;

@Data
public class TeamTagRow {
    private Long articleId;
    private Long tagId;
    private String name;
    private String color;
    private Integer articleCount;
}
