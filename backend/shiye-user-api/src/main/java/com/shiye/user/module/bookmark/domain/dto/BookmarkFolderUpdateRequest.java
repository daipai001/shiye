package com.shiye.user.module.bookmark.domain.dto;

public record BookmarkFolderUpdateRequest(
        Boolean isPrimary,
        Boolean autoSync
) {
}
