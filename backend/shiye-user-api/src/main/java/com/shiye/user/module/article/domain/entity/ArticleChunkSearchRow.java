package com.shiye.user.module.article.domain.entity;

import lombok.Data;

@Data
public class ArticleChunkSearchRow {
    private Long id;
    private Long articleId;
    private Long userId;
    private String content;
    private Integer charStart;
    private Integer charEnd;
    private Double score;
}
