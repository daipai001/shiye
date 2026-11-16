package com.shiye.admin.module.auth.domain.vo;

import com.shiye.admin.module.auth.domain.entity.AdminUserEntity;

public record AdminSummary(
        String id,
        String email,
        String name,
        String role
) {
    public static AdminSummary from(AdminUserEntity admin) {
        return new AdminSummary(
                String.valueOf(admin.getId()),
                admin.getEmail(),
                admin.getName(),
                admin.getRole()
        );
    }
}
