package com.shiye.admin.module.auth.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminUserEntity {
    private Long id;
    private String email;
    private String passwordHash;
    private String name;
    private String role;
    private String status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
