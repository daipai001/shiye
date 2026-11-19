package com.shiye.admin.module.eval.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EvalRunEntity {
    private Long id;
    private Long userId;
    private String userName;
    private String name;
    private String model;
    private String embeddingModel;
    private Integer topK;
    private Boolean rerank;
    private String metrics;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
}
