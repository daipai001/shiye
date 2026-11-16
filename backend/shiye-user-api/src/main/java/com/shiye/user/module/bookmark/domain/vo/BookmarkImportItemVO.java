package com.shiye.user.module.bookmark.domain.vo;

import com.shiye.user.module.bookmark.domain.entity.BookmarkItemEntity;

public record BookmarkImportItemVO(
        String id,
        String title,
        String url,
        String folderPath,
        String status,
        String articleId,
        String errorCode,
        String errorMessage
) {

    public static BookmarkImportItemVO from(BookmarkItemEntity entity) {
        return new BookmarkImportItemVO(
                String.valueOf(entity.getId()),
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
