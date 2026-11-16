package com.shiye.user.module.auth.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RefreshTokenEntity {
    private Long id;
    private String subjectType;
    private Long subjectId;
    private String tokenHash;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private Long replacedById;
    private String userAgent;
    private String ip;
    private LocalDateTime createdAt;
}
