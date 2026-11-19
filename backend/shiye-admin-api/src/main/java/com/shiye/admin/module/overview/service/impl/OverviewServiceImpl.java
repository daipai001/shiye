package com.shiye.admin.module.overview.service.impl;

import com.shiye.admin.module.overview.domain.entity.SourceSplitRow;
import com.shiye.admin.module.overview.domain.entity.UsageSummaryRow;
import com.shiye.admin.module.overview.domain.entity.UsageTrendRow;
import com.shiye.admin.module.overview.domain.vo.FailureVO;
import com.shiye.admin.module.overview.domain.vo.OverviewVO;
import com.shiye.admin.module.overview.domain.vo.SourceSplitVO;
import com.shiye.admin.module.overview.domain.vo.TrendPointVO;
import com.shiye.admin.module.overview.mapper.OverviewMapper;
import com.shiye.admin.module.overview.service.OverviewService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OverviewServiceImpl implements OverviewService {

    private final OverviewMapper overviewMapper;

    public OverviewServiceImpl(OverviewMapper overviewMapper) {
        this.overviewMapper = overviewMapper;
    }

    @Override
    public OverviewVO overview() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        var todayStart = today.atStartOfDay();
        var trendStart = today.minusDays(6).atStartOfDay();

        UsageSummaryRow todayUsage = overviewMapper.usageSince(todayStart);
        long calls = todayUsage == null || todayUsage.getCalls() == null ? 0 : todayUsage.getCalls();
        long tokens = todayUsage == null || todayUsage.getTokens() == null ? 0 : todayUsage.getTokens();
        BigDecimal cost = todayUsage == null || todayUsage.getCost() == null
                ? BigDecimal.ZERO : todayUsage.getCost();
        long errors = todayUsage == null || todayUsage.getErrors() == null ? 0 : todayUsage.getErrors();
        double errorRate = calls == 0 ? 0 : (double) errors / calls;
        Integer p95 = overviewMapper.p95Latency(todayStart);
        if (p95 == null) {
            p95 = overviewMapper.maxLatencySince(todayStart);
        }

        Map<LocalDate, UsageTrendRow> trendRows = new HashMap<>();
        for (UsageTrendRow row : overviewMapper.trendsSince(trendStart)) {
            trendRows.put(row.getDay(), row);
        }
        List<TrendPointVO> trends = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate day = today.minusDays(6 - i);
            UsageTrendRow row = trendRows.get(day);
            trends.add(new TrendPointVO(
                    day.toString(),
                    row == null || row.getCalls() == null ? 0 : row.getCalls(),
                    row == null || row.getTokens() == null ? 0 : row.getTokens(),
                    row == null || row.getCost() == null ? BigDecimal.ZERO : row.getCost()
            ));
        }

        List<SourceSplitVO> sourceSplit = overviewMapper.sourceSplitSince(todayStart).stream()
                .map(this::toSourceSplit)
                .toList();

        return new OverviewVO(
                overviewMapper.countUsers(),
                overviewMapper.countArticles(),
                overviewMapper.countChunks(),
                calls,
                tokens,
                cost,
                BigDecimal.valueOf(errorRate).setScale(4, RoundingMode.HALF_UP).doubleValue(),
                p95 == null ? 0 : p95,
                trends,
                sourceSplit,
                overviewMapper.recentFailures().stream().map(FailureVO::from).toList()
        );
    }

    private SourceSplitVO toSourceSplit(SourceSplitRow row) {
        return new SourceSplitVO(
                row.getSource(),
                row.getCalls() == null ? 0 : row.getCalls(),
                row.getTokens() == null ? 0 : row.getTokens(),
                row.getCost() == null ? BigDecimal.ZERO : row.getCost()
        );
    }
}
