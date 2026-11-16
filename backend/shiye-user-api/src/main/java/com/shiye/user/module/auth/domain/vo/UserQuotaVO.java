package com.shiye.user.module.auth.domain.vo;

import java.math.BigDecimal;

public record UserQuotaVO(
        long monthlyTokenQuota,
        BigDecimal monthlyCostQuota
) {
}
