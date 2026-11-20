package com.shiye.admin.module.auth.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AdminAccountStatusRequest(
        @NotBlank
        @Pattern(regexp = "ACTIVE|DISABLED", message = "status 仅支持 ACTIVE / DISABLED")
        String status
) {
}
