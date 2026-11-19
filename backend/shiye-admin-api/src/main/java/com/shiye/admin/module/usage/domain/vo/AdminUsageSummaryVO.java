package com.shiye.admin.module.usage.domain.vo;

import java.math.BigDecimal;
import java.util.List;

public record AdminUsageSummaryVO(
        long calls,
        long tokens,
        BigDecimal cost,
        double errorRate,
        List<AdminUsageGroupVO> sourceSplit,
        List<AdminUsageGroupVO> modelSplit
) {
}
