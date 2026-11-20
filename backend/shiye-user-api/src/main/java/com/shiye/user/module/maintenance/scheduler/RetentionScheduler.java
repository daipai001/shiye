package com.shiye.user.module.maintenance.scheduler;

import com.shiye.user.module.maintenance.service.RetentionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "shiye.retention.enabled", havingValue = "true", matchIfMissing = true)
public class RetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetentionScheduler.class);

    private final RetentionService retentionService;

    public RetentionScheduler(RetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @Scheduled(cron = "${shiye.retention.cron:0 30 3 * * *}")
    public void tick() {
        RetentionService.RetentionResult result = retentionService.cleanup();
        if (result.refreshTokens() > 0 || result.extensionCodes() > 0
                || result.idempotencyKeys() > 0 || result.ingestTasks() > 0) {
            log.info("retention cleanup: refreshTokens={}, extensionCodes={}, "
                            + "idempotencyKeys={}, ingestTasks={}",
                    result.refreshTokens(), result.extensionCodes(),
                    result.idempotencyKeys(), result.ingestTasks());
        }
    }
}
