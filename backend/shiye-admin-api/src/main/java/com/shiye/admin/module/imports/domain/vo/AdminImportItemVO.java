package com.shiye.admin.module.imports.domain.vo;

import com.shiye.admin.module.imports.domain.entity.AdminImportItemRowEntity;

public record AdminImportItemVO(
        String id,
        String userId,
        String userEmail,
        String title,
        String url,
        String folderPath,
        String status,
        String articleId,
        String errorCode,
        String errorMessage
) {

    public static AdminImportItemVO from(AdminImportItemRowEntity entity) {
        return new AdminImportItemVO(
                String.valueOf(entity.getId()),
                String.valueOf(entity.getUserId()),
                entity.getUserEmail(),
                entity.getTitle(),
                entity.getUrl(),
                entity.getFolderPath(),
                entity.getStatus(),
                entity.getArticleId() == null ? null : String.valueOf(entity.getArticleId()),
                entity.getErrorCode(),
                entity.getErrorMessage()
        );
    }
}
