package com.shiye.user.module.bookmark.domain.vo;

import com.shiye.user.module.bookmark.domain.entity.BookmarkFolderEntity;

import java.time.LocalDateTime;

public record BookmarkFolderVO(
        String id,
        String browser,
        String folderId,
        String folderPath,
        boolean isPrimary,
        boolean autoSync,
        LocalDateTime lastSyncedAt
) {

    public static BookmarkFolderVO from(BookmarkFolderEntity entity) {
        return new BookmarkFolderVO(
                String.valueOf(entity.getId()),
                entity.getBrowser(),
                entity.getFolderId(),
                entity.getFolderPath(),
                Boolean.TRUE.equals(entity.getIsPrimary()),
                Boolean.TRUE.equals(entity.getAutoSync()),
                entity.getLastSyncedAt()
        );
    }
}
