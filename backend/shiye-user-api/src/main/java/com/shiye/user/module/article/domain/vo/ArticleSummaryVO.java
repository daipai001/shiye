package com.shiye.user.module.article.domain.vo;

import com.shiye.user.module.article.domain.entity.ArticleEntity;

import java.time.LocalDateTime;

public record ArticleSummaryVO(
        String id,
        String title,
        String domain,
        String url,
        String sourceType,
        String summary,
        String status,
        Integer wordCount,
        Integer chunkCount,
        String language,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ArticleSummaryVO from(ArticleEntity entity) {
        return new ArticleSummaryVO(
                String.valueOf(entity.getId()),
                entity.getTitle(),
                entity.getDomain(),
                entity.getUrl(),
                entity.getSourceType(),
                entity.getSummary(),
                entity.getStatus(),
                entity.getWordCount() == null ? 0 : entity.getWordCount(),
                entity.getChunkCount() == null ? 0 : entity.getChunkCount(),
                entity.getLanguage(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
