package com.shiye.admin.module.imports.domain.vo;

import com.shiye.admin.module.imports.domain.entity.AdminImportRowEntity;

import java.time.LocalDateTime;

public record AdminImportVO(
        String id,
        String userId,
        String userEmail,
        String folderPath,
        String status,
        int total,
        int success,
        int failed,
        int skipped,
        String errorCode,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt
) {

    public static AdminImportVO from(AdminImportRowEntity entity) {
        return new AdminImportVO(
                String.valueOf(entity.getId()),
                String.valueOf(entity.getUserId()),
                entity.getUserEmail(),
                entity.getFolderPath(),
                entity.getStatus(),
                value(entity.getTotal()),
                value(entity.getSuccess()),
                value(entity.getFailed()),
                value(entity.getSkipped()),
                entity.getErrorCode(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getCreatedAt()
        );
    }

    private static int value(Integer number) {
        return number == null ? 0 : number;
    }
}
