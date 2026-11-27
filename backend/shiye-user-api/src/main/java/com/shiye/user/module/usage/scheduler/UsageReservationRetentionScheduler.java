package com.shiye.user.module.usage.scheduler;

import com.shiye.user.module.usage.mapper.UsageReservationMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 清理已释放的预留流水，避免 usage_reservations 无限增长。
 * 只删除 RELEASED 且超过保留期的记录，ACTIVE/过期未回收的流水由 reaper 处理。
 */
@Component
public class UsageReservationRetentionScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(UsageReservationRetentionScheduler.class);
    private static final int MAX_BATCHES = 20;

    private final UsageReservationMapper reservationMapper;
    private final boolean enabled;
    private final int retentionDays;
    private final int batchSize;
    private final Counter purged;

    public UsageReservationRetentionScheduler(
            UsageReservationMapper reservationMapper,
            @Value("${shiye.usage.reservation-retention-enabled:true}") boolean enabled,
            @Value("${shiye.usage.reservation-retention-days:90}") int retentionDays,
            @Value("${shiye.usage.reservation-retention-batch:5000}") int batchSize,
            MeterRegistry meterRegistry) {
        this.reservationMapper = reservationMapper;
        this.enabled = enabled;
        this.retentionDays = Math.max(1, retentionDays);
        this.batchSize = Math.max(100, batchSize);
        this.purged = Counter.builder("shiye.usage.reservations.purged")
                .description("已清理的过期预留流水")
                .register(meterRegistry);
    }

    @Scheduled(cron = "${shiye.usage.reservation-retention-cron:0 40 3 * * *}")
    public void purge() {
        if (!enabled) {
            return;
        }
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC)
                .minusDays(retentionDays);
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            int deleted = reservationMapper.deleteReleasedBefore(before, batchSize);
            if (deleted <= 0) {
                break;
            }
            total += deleted;
            purged.increment(deleted);
            if (deleted < batchSize) {
                break;
            }
        }
        if (total > 0) {
            log.info("purged {} released usage reservations older than {} days",
                    total, retentionDays);
        }
    }
}
