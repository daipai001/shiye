package com.shiye.user.module.file.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FileAssetEntity {
    private Long id;
    private Long userId;
    private String storageType;
    private String bucket;
    private String objectKey;
    private String originalName;
    private String mimeType;
    private Long sizeBytes;
    private String sha256;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
