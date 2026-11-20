package com.shiye.admin.module.audit.service;

import com.shiye.admin.module.audit.mapper.AdminAuditMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 审计日志保留策略：删除超过保留天数的记录。
 */
@Service
public class AdminAuditRetentionService {

    private final AdminAuditMapper auditMapper;

    @Value("${shiye.audit.retention-days:180}")
    private int retentionDays;

    public AdminAuditRetentionService(AdminAuditMapper auditMapper) {
        this.auditMapper = auditMapper;
    }

    public int cleanup() {
        int days = Math.max(1, retentionDays);
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusDays(days);
        return auditMapper.deleteOlderThan(cutoff);
    }
}
