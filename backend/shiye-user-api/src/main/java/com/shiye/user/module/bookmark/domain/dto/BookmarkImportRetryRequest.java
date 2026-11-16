package com.shiye.user.module.bookmark.domain.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

public record BookmarkImportRetryRequest(
        @Size(max = 50) List<Long> itemIds
) {
}
