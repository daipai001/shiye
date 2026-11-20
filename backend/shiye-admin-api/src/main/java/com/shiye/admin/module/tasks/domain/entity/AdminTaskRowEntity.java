package com.shiye.admin.module.tasks.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminTaskRowEntity {
    private Long id;
    private Long importId;
    private Long userId;
    private String userEmail;
    private String title;
    private String url;
    private String stage;
    private String sourceType;
    private String status;
    private Integer retryCount;
    private Long articleId;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
