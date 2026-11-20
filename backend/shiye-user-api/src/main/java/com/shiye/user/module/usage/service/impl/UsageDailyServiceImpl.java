package com.shiye.user.module.usage.service.impl;

import com.shiye.user.module.usage.mapper.AiUsageDailyMapper;
import com.shiye.user.module.usage.service.UsageDailyService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;

@Service
public class UsageDailyServiceImpl implements UsageDailyService {

    private final AiUsageDailyMapper mapper;

    public UsageDailyServiceImpl(AiUsageDailyMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int aggregate(LocalDate date) {
        return mapper.aggregate(date.atStartOfDay(), date.plusDays(1).atStartOfDay());
    }

    @Override
    public int aggregateRecent(int days) {
        int safeDays = Math.max(1, Math.min(days, 31));
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        int rows = 0;
        for (int i = 1; i <= safeDays; i++) {
            rows += aggregate(today.minusDays(i));
        }
        return rows;
    }
}
