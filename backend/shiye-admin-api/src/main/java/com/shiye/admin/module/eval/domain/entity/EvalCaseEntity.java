package com.shiye.admin.module.eval.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EvalCaseEntity {
    private Long id;
    private Long userId;
    private String userEmail;
    private String question;
    private String expectedArticleIds;
    private String expectedChunkIds;
    private String goldAnswer;
    private String tags;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
