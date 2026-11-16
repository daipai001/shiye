package com.shiye.user.module.auth.domain.vo;

import com.shiye.user.module.auth.domain.entity.UserEntity;

import java.time.LocalDateTime;

public record UserSummary(
        String id,
        String email,
        String displayName,
        String avatarUrl,
        String status,
        LocalDateTime createdAt
) {
    public static UserSummary from(UserEntity user) {
        return new UserSummary(
                String.valueOf(user.getId()),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }
}
