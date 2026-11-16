package com.shiye.user.module.auth.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExtensionTokenRequest(
        @NotBlank @Size(max = 64) String code,
        @Size(max = 512) String clientInfo
) {
}
