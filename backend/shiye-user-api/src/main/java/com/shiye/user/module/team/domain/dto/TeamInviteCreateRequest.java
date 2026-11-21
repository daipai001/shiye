package com.shiye.user.module.team.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TeamInviteCreateRequest(
        @NotBlank
        @Pattern(regexp = "ADMIN|MEMBER", message = "role 仅支持 ADMIN / MEMBER")
        String role,

        @Email
        String email
) {
}
