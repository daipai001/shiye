package com.shiye.user.module.auth.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExtensionAuthCodeEntity {
    private Long id;
    private Long userId;
    private String codeHash;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private LocalDateTime createdAt;
}
