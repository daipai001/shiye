package com.shiye.admin.module.auth.domain.vo;

import com.shiye.admin.module.auth.domain.entity.AdminUserEntity;

import java.time.LocalDateTime;

public record AdminAccountVO(
        String id,
        String email,
        String name,
        String role,
        String status,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AdminAccountVO from(AdminUserEntity admin) {
        return new AdminAccountVO(
                String.valueOf(admin.getId()),
                admin.getEmail(),
                admin.getName(),
                admin.getRole(),
                admin.getStatus(),
                admin.getLastLoginAt(),
                admin.getCreatedAt(),
                admin.getUpdatedAt()
        );
    }
}
