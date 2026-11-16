package com.shiye.user.module.article.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ArticleChunkEntity {
    private Long id;
    private Long articleId;
    private Long userId;
    private Integer chunkIndex;
    private String content;
    private Integer tokenCount;
    private Integer charStart;
    private Integer charEnd;
    private String embeddingModel;
    private Integer embeddingDim;
    private String vectorId;
    private String contentHash;
    private LocalDateTime createdAt;
}
