package com.shiye.admin.module.imports.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminImportRowEntity {
    private Long id;
    private Long userId;
    private String userEmail;
    private String folderPath;
    private String status;
    private Integer total;
    private Integer success;
    private Integer failed;
    private Integer skipped;
    private String errorCode;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
}
