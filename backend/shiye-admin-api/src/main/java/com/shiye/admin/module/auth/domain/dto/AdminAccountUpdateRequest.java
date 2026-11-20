package com.shiye.admin.module.auth.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminAccountUpdateRequest(
        @NotBlank
        @Size(max = 64)
        String name,

        @NotBlank
        @Pattern(regexp = "SUPER_ADMIN|OPERATOR|VIEWER",
                message = "role 仅支持 SUPER_ADMIN / OPERATOR / VIEWER")
        String role
) {
}
