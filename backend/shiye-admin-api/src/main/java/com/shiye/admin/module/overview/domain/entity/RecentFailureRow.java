package com.shiye.admin.module.overview.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RecentFailureRow {
    private String type;
    private String resourceId;
    private String userName;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime createdAt;
}
