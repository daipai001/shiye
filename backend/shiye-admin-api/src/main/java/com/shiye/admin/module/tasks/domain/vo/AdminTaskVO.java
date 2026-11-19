package com.shiye.admin.module.tasks.domain.vo;

import com.shiye.admin.module.tasks.domain.entity.AdminTaskRowEntity;

import java.time.LocalDateTime;

public record AdminTaskVO(
        String id,
        String articleId,
        String userId,
        String userEmail,
        String title,
        String url,
        String stage,
        String status,
        String errorCode,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime finishedAt
) {

    public static AdminTaskVO from(AdminTaskRowEntity entity) {
        return new AdminTaskVO(
                String.valueOf(entity.getId()),
                entity.getArticleId() == null ? null : String.valueOf(entity.getArticleId()),
                String.valueOf(entity.getUserId()),
                entity.getUserEmail(),
                entity.getTitle(),
                entity.getUrl(),
                entity.getStatus(),
                entity.getStatus(),
                entity.getErrorCode(),
                entity.getErrorMessage(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
