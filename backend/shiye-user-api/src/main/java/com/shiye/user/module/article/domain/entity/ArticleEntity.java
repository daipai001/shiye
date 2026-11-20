package com.shiye.user.module.article.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ArticleEntity {
    private Long id;
    private Long userId;
    private Long teamId;
    private String title;
    private String url;
    private String urlHash;
    private String domain;
    private String sourceType;
    private String author;
    private LocalDateTime publishedAt;
    private LocalDateTime fetchedAt;
    private String summary;
    private String contentText;
    private String contentHash;
    private Integer wordCount;
    private String language;
    private String status;
    private Long fileAssetId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    private Integer chunkCount;
}
