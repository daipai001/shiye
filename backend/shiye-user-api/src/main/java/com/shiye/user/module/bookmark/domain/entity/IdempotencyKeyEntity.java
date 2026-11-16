package com.shiye.user.module.bookmark.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IdempotencyKeyEntity {
    private Long id;
    private Long userId;
    private String endpoint;
    private String idempotencyKey;
    private String requestHash;
    private Integer responseCode;
    private String responseBody;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
