package com.shiye.admin.module.eval.domain.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class EvalResultEntity {
    private Long id;
    private Long runId;
    private Long caseId;
    private String question;
    private String retrievedChunkIds;
    private BigDecimal recallAt5;
    private BigDecimal citationAccuracy;
    private BigDecimal answerCorrectness;
    private String answerText;
    private String judgeReason;
    private Integer latencyMs;
    private BigDecimal cost;
    private LocalDateTime createdAt;
}
