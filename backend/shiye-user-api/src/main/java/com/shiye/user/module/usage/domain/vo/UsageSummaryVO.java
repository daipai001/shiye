package com.shiye.user.module.usage.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record UsageSummaryVO(
        LocalDate from,
        LocalDate to,
        int days,
        long calls,
        long tokens,
        BigDecimal cost,
        long errors,
        List<UsageBreakdownVO> scenes,
        List<UsageBreakdownVO> sources,
        List<UsageDailyVO> daily
) {
}
