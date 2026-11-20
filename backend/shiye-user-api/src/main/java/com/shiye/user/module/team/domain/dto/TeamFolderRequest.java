package com.shiye.user.module.team.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeamFolderRequest(
        @NotBlank
        @Size(max = 64)
        String name
) {
}
