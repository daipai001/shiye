package com.shiye.admin.module.auth.domain.vo;

public record AdminTokenResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        String tokenType,
        AdminSummary admin
) {
}
