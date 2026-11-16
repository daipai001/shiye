package com.shiye.user.module.bookmark.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BookmarkImportEntity {
    private Long id;
    private Long userId;
    private Long bookmarkFolderId;
    private String browser;
    private String folderId;
    private String folderPath;
    private Boolean recursive;
    private String status;
    private Integer total;
    private Integer success;
    private Integer failed;
    private Integer skipped;
    private String errorCode;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
