package com.shiye.user.module.article.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ArticleFolderEntity {
    private Long id;
    private Long articleId;
    private Long userId;
    private Long bookmarkFolderId;
    private String folderPath;
    private LocalDateTime createdAt;
}
