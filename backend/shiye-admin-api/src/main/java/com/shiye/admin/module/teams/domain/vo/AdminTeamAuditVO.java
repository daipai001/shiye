package com.shiye.admin.module.teams.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;
import com.shiye.admin.module.teams.domain.entity.AdminTeamAuditRowEntity;

import java.time.LocalDateTime;

public record AdminTeamAuditVO(
        String id,
        String teamId,
        String teamName,
        String operatorId,
        String operatorEmail,
        String action,
        String targetType,
        String targetId,
        JsonNode detail,
        String ip,
        String userAgent,
        LocalDateTime createdAt
) {

    public static AdminTeamAuditVO from(AdminTeamAuditRowEntity row, JsonNode detail) {
        return new AdminTeamAuditVO(
                String.valueOf(row.getId()),
                String.valueOf(row.getTeamId()),
                row.getTeamName(),
                String.valueOf(row.getOperatorId()),
                row.getOperatorEmail(),
                row.getAction(),
                row.getTargetType(),
                row.getTargetId(),
                detail,
                row.getIp(),
                row.getUserAgent(),
                row.getCreatedAt()
        );
    }
}
