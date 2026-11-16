package com.shiye.user.module.bookmark.domain.vo;

public record BookmarkImportCreateResponse(
        String importId,
        String status,
        int total
) {
}
