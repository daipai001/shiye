package com.shiye.user.module.bookmark.domain.vo;

import com.shiye.user.module.bookmark.domain.entity.BookmarkImportEntity;

import java.time.LocalDateTime;

public record BookmarkImportSummaryVO(
        String id,
        String folderPath,
        String status,
        int total,
        int success,
        int failed,
        int skipped,
        String errorCode,
        LocalDateTime createdAt,
        LocalDateTime finishedAt
) {

    public static BookmarkImportSummaryVO from(BookmarkImportEntity entity) {
        return new BookmarkImportSummaryVO(
                String.valueOf(entity.getId()),
                entity.getFolderPath(),
                entity.getStatus(),
                value(entity.getTotal()),
                value(entity.getSuccess()),
                value(entity.getFailed()),
                value(entity.getSkipped()),
                entity.getErrorCode(),
                entity.getCreatedAt(),
                entity.getFinishedAt()
        );
    }

    private static int value(Integer number) {
        return number == null ? 0 : number;
    }
}
