package com.shiye.user.module.ingest.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IngestTaskEntity {
    private Long id;
    private Long userId;
    private Long articleId;
    private String sourceType;
    private String stage;
    private String status;
    private String idempotencyKey;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
