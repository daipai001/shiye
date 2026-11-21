package com.shiye.user.module.auth.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PasswordForgotRequest(
        @NotBlank
        @Email
        String email
) {
}
