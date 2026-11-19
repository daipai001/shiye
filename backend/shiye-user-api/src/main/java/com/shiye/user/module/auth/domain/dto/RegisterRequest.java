package com.shiye.user.module.auth.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 190) String email,
        @NotBlank
        @Size(min = 8, max = 72)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$", message = "密码需同时包含字母和数字")
        String password,
        @Size(max = 64) String displayName,
        @Size(max = 64) String invitationCode
) {
}
