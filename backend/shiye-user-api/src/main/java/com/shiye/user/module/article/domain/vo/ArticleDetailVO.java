package com.shiye.user.module.article.domain.vo;

import com.shiye.user.module.article.domain.entity.ArticleEntity;
import com.shiye.user.module.tag.domain.vo.TagVO;

import java.time.LocalDateTime;
import java.util.List;

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
        List<TagVO> tags,
        LocalDateTime fetchedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ArticleDetailVO from(ArticleEntity entity) {
        return from(entity, List.of());
    }

    public static ArticleDetailVO from(ArticleEntity entity, List<TagVO> tags) {
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
                tags == null ? List.of() : tags,
                entity.getFetchedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
