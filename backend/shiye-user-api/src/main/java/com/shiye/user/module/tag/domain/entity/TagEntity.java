package com.shiye.user.module.tag.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TagEntity {
    private Long id;
    private Long userId;
    private String name;
    private String normalizedName;
    private String color;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
