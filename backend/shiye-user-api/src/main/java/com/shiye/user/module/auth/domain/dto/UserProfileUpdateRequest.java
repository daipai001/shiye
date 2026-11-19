package com.shiye.user.module.auth.domain.dto;

import jakarta.validation.constraints.Size;

public record UserProfileUpdateRequest(
        @Size(max = 64) String displayName,
        @Size(max = 512) String avatarUrl
) {
}
