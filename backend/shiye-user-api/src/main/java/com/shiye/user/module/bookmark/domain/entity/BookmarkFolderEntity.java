package com.shiye.user.module.bookmark.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BookmarkFolderEntity {
    private Long id;
    private Long userId;
    private String browser;
    private String folderId;
    private String folderPath;
    private Boolean isPrimary;
    private Boolean autoSync;
    private LocalDateTime lastSyncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
