package com.shiye.user.module.article.domain.vo;

import com.shiye.user.module.article.domain.entity.ArticleEntity;

import java.time.LocalDateTime;

public record ArticleDetailVO(
        String id,
        String title,
        String domain,
        String url,
        String sourceType,
        String author,
        LocalDateTime publishedAt,
        String summary,
        String contentText,
        boolean contentTruncated,
        String status,
        Integer wordCount,
        Integer chunkCount,
        String language,
        LocalDateTime fetchedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ArticleDetailVO from(ArticleEntity entity) {
        return new ArticleDetailVO(
                String.valueOf(entity.getId()),
                entity.getTitle(),
                entity.getDomain(),
                entity.getUrl(),
                entity.getSourceType(),
                entity.getAuthor(),
                entity.getPublishedAt(),
                entity.getSummary(),
                entity.getContentText(),
                false,
                entity.getStatus(),
                entity.getWordCount() == null ? 0 : entity.getWordCount(),
                entity.getChunkCount() == null ? 0 : entity.getChunkCount(),
                entity.getLanguage(),
                entity.getFetchedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
