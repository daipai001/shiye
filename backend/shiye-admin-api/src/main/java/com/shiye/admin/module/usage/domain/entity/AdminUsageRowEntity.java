package com.shiye.admin.module.usage.domain.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AdminUsageRowEntity {
    private Long id;
    private String requestId;
    private Long userId;
    private String userEmail;
    private String source;
    private String scene;
    private String model;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private BigDecimal cost;
    private String currency;
    private Integer latencyMs;
    private Integer firstTokenMs;
    private String status;
    private String errorCode;
    private LocalDateTime createdAt;
}
