package com.shiye.admin.module.overview.domain.vo;

import java.math.BigDecimal;
import java.util.List;

public record OverviewVO(
        long users,
        long articles,
        long chunks,
        long todayCalls,
        long todayTokens,
        BigDecimal todayCost,
        double errorRate,
        int p95LatencyMs,
        List<TrendPointVO> trends,
        List<SourceSplitVO> sourceSplit,
        List<FailureVO> recentFailures
) {
}
