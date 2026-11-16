package com.shiye.user.module.bookmark.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BookmarkImportCreateRequest(
        @NotNull @Valid Folder folder,
        @NotEmpty @Size(max = 50) List<@Valid Item> items,
        @Pattern(regexp = "\\d+", message = "importId 必须是数字") String importId
) {

    public record Folder(
            @NotBlank @Size(max = 16) String browser,
            @NotBlank @Size(max = 128) String folderId,
            @NotBlank @Size(max = 512) String folderPath,
            Boolean recursive
    ) {
    }

    public record Item(
            @Size(max = 512) String title,
            @NotBlank @Size(max = 2048) String url,
            @Size(max = 512) String parentPath
    ) {
    }
}
