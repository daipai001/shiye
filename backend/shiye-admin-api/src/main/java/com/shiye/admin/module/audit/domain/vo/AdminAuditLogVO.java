package com.shiye.admin.module.audit.domain.vo;

import com.shiye.admin.module.audit.domain.entity.AdminAuditLogRow;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public record AdminAuditLogVO(
        String id,
        String adminId,
        String adminEmail,
        String action,
        String resourceType,
        String resourceId,
        JsonNode detail,
        String ip,
        String userAgent,
        LocalDateTime createdAt
) {

    public static AdminAuditLogVO from(AdminAuditLogRow row, JsonNode detail) {
        return new AdminAuditLogVO(
                String.valueOf(row.getId()),
                String.valueOf(row.getAdminId()),
                row.getAdminEmail(),
                row.getAction(),
                row.getResourceType(),
                row.getResourceId(),
                detail,
                row.getIp(),
                row.getUserAgent(),
                row.getCreatedAt()
        );
    }
}
