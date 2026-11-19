package com.shiye.user.module.auth.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserPasswordUpdateRequest(
        @NotBlank @Size(max = 128) String oldPassword,
        @NotBlank @Size(min = 8, max = 72) String newPassword
) {
}
