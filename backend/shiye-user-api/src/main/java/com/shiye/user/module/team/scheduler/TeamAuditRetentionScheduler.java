package com.shiye.user.module.team.scheduler;

import com.shiye.user.module.team.mapper.TeamAuditLogMapper;
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
 * 团队审计流水保留策略，默认 180 天。
 */
@Component
public class TeamAuditRetentionScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(TeamAuditRetentionScheduler.class);
    private static final int MAX_BATCHES = 20;

    private final TeamAuditLogMapper auditMapper;
    private final boolean enabled;
    private final int retentionDays;
    private final int batchSize;
    private final Counter purged;

    public TeamAuditRetentionScheduler(
            TeamAuditLogMapper auditMapper,
            @Value("${shiye.team.audit-retention-enabled:true}") boolean enabled,
            @Value("${shiye.team.audit-retention-days:180}") int retentionDays,
            @Value("${shiye.team.audit-retention-batch:5000}") int batchSize,
            MeterRegistry meterRegistry) {
        this.auditMapper = auditMapper;
        this.enabled = enabled;
        this.retentionDays = Math.max(1, retentionDays);
        this.batchSize = Math.max(100, batchSize);
        this.purged = Counter.builder("shiye.team.audit.purged")
                .description("已清理的团队审计流水")
                .register(meterRegistry);
    }

    @Scheduled(cron = "${shiye.team.audit-retention-cron:0 50 3 * * *}")
    public void purge() {
        if (!enabled) {
            return;
        }
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC)
                .minusDays(retentionDays);
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            int deleted = auditMapper.deleteBefore(before, batchSize);
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
            log.info("purged {} team audit logs older than {} days",
                    total, retentionDays);
        }
    }
}
