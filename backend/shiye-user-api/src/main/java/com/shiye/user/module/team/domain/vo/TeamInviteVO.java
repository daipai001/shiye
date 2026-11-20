package com.shiye.user.module.team.domain.vo;

import java.time.LocalDateTime;

public record TeamInviteVO(
        String code,
        String role,
        LocalDateTime expiresAt
) {
}
