package com.shiye.admin.module.teams.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminTeamAuditRowEntity {
    private Long id;
    private Long teamId;
    private String teamName;
    private Long operatorId;
    private String operatorEmail;
    private String action;
    private String targetType;
    private String targetId;
    private String detail;
    private String ip;
    private String userAgent;
    private LocalDateTime createdAt;
}
