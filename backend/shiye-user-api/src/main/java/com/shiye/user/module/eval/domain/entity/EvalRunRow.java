package com.shiye.user.module.eval.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EvalRunRow {
    private Long id;
    private Long userId;
    private String name;
    private String model;
    private Integer topK;
    private Boolean rerank;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
