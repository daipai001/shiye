package com.shiye.admin.module.auth.domain.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminRefreshRequest(@NotBlank String refreshToken) {
}
