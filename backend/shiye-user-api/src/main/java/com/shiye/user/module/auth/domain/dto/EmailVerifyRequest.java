package com.shiye.user.module.auth.domain.dto;

import jakarta.validation.constraints.NotBlank;

public record EmailVerifyRequest(
        @NotBlank
        String token
) {
}
