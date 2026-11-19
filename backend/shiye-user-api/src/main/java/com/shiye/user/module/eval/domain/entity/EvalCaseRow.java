package com.shiye.user.module.eval.domain.entity;

import lombok.Data;

@Data
public class EvalCaseRow {
    private Long id;
    private Long userId;
    private String question;
    private String expectedArticleIds;
    private String expectedChunkIds;
    private String goldAnswer;
}
