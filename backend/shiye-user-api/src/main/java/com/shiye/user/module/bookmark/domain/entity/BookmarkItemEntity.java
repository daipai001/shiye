package com.shiye.user.module.bookmark.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BookmarkItemEntity {
    private Long id;
    private Long importId;
    private Long userId;
    private String title;
    private String url;
    private String urlHash;
    private String browserFolderId;
    private String folderPath;
    private String status;
    private Long articleId;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
