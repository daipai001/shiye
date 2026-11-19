package com.shiye.user.module.tag.domain.entity;

import lombok.Data;

@Data
public class TagRow {
    private Long tagId;
    private Long articleId;
    private String name;
    private String color;
    private Integer articleCount;
}
