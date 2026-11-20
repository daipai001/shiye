package com.shiye.user.module.usage.scheduler;

import com.shiye.user.module.usage.service.UsageDailyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 把 ai_usage 聚合到 ai_usage_daily；每次回补最近 N 天，任务可重复执行。
 */
@Component
@ConditionalOnProperty(name = "shiye.usage.daily-aggregation-enabled",
        havingValue = "true", matchIfMissing = true)
public class AiUsageDailyScheduler {

    private static final Logger log = LoggerFactory.getLogger(AiUsageDailyScheduler.class);

    private final UsageDailyService dailyService;
    private final int backfillDays;

    public AiUsageDailyScheduler(
            UsageDailyService dailyService,
            @Value("${shiye.usage.daily-backfill-days:7}") int backfillDays) {
        this.dailyService = dailyService;
        this.backfillDays = Math.max(1, Math.min(backfillDays, 31));
    }

    @Scheduled(cron = "${shiye.usage.daily-cron:0 15 0 * * *}")
    public void tick() {
        int rows = dailyService.aggregateRecent(backfillDays);
        log.info("ai usage daily aggregation finished: days={}, affectedRows={}",
                backfillDays, rows);
    }
}
