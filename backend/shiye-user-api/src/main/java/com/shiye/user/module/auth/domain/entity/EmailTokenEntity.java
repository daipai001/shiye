package com.shiye.user.module.auth.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EmailTokenEntity {
    private Long id;
    private Long userId;
    private String type;
    private String tokenHash;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private LocalDateTime createdAt;
}
