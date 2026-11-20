package com.shiye.user.module.team.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TeamMemberAddRequest(
        @NotBlank
        @Email
        @Size(max = 190)
        String email,

        @NotBlank
        @Pattern(regexp = "ADMIN|MEMBER", message = "role 仅支持 ADMIN / MEMBER")
        String role
) {
}
