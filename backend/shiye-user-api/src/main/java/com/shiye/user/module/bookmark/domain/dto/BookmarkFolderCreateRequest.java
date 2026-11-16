package com.shiye.user.module.bookmark.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BookmarkFolderCreateRequest(
        @NotBlank @Size(max = 16) String browser,
        @NotBlank @Size(max = 128) String folderId,
        @NotBlank @Size(max = 512) String folderPath,
        Boolean isPrimary,
        Boolean autoSync
) {
}
