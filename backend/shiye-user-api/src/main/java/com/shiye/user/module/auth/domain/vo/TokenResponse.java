package com.shiye.user.module.auth.domain.vo;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        String tokenType,
        UserSummary user
) {
}
