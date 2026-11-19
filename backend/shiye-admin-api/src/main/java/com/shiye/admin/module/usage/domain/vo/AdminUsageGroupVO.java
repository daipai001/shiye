package com.shiye.admin.module.usage.domain.vo;

import com.shiye.admin.module.usage.domain.entity.AdminUsageGroupRow;

import java.math.BigDecimal;

public record AdminUsageGroupVO(
        String key,
        long calls,
        long tokens,
        BigDecimal cost
) {

    public static AdminUsageGroupVO from(AdminUsageGroupRow row) {
        return new AdminUsageGroupVO(
                row.getKey(),
                row.getCalls() == null ? 0 : row.getCalls(),
                row.getTokens() == null ? 0 : row.getTokens(),
                row.getCost() == null ? BigDecimal.ZERO : row.getCost()
        );
    }
}
