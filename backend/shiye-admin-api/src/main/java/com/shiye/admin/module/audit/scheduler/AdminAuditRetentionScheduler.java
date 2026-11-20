package com.shiye.admin.module.audit.scheduler;

import com.shiye.admin.module.audit.service.AdminAuditRetentionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "shiye.audit.retention-enabled",
        havingValue = "true", matchIfMissing = true)
public class AdminAuditRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditRetentionScheduler.class);

    private final AdminAuditRetentionService retentionService;

    public AdminAuditRetentionScheduler(AdminAuditRetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @Scheduled(cron = "${shiye.audit.retention-cron:0 30 3 * * *}", zone = "UTC")
    public void cleanup() {
        int deleted = retentionService.cleanup();
        if (deleted > 0) {
            log.info("audit retention cleanup deleted {} rows", deleted);
        }
    }
}
