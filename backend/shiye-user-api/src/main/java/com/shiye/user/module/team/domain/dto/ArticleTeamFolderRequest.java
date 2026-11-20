package com.shiye.user.module.team.domain.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ArticleTeamFolderRequest(
        @Pattern(regexp = "\\d+", message = "folderId 必须是数字")
        String folderId,

        @Size(max = 64)
        String name
) {
}
