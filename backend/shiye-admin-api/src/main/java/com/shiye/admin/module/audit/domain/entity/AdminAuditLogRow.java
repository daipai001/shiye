package com.shiye.admin.module.audit.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminAuditLogRow {
    private Long id;
    private Long adminId;
    private String adminEmail;
    private String action;
    private String resourceType;
    private String resourceId;
    private String detail;
    private String ip;
    private String userAgent;
    private LocalDateTime createdAt;
}
