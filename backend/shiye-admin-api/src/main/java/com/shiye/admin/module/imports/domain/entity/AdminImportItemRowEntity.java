package com.shiye.admin.module.imports.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminImportItemRowEntity {
    private Long id;
    private Long userId;
    private String userEmail;
    private String title;
    private String url;
    private String folderPath;
    private String status;
    private Long articleId;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime updatedAt;
}
