package com.shiye.user.module.team.domain.vo;

import java.math.BigDecimal;

public record TeamQuota(
        long tokenQuota,
        BigDecimal costQuota
) {
}
