package com.shiye.user.module.usage.domain.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AiUsageEntity {
    private Long id;
    private String requestId;
    private Long userId;
    private String source;
    private String scene;
    private String model;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Integer cachedTokens;
    private BigDecimal inputPrice;
    private BigDecimal outputPrice;
    private BigDecimal cost;
    private String currency;
    private Integer latencyMs;
    private Integer firstTokenMs;
    private String status;
    private String errorCode;
    private Boolean estimated;
    private Long conversationId;
    private Long articleId;
    private String clientVersion;
    private LocalDateTime createdAt;
}
