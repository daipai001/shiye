package com.shiye.user.module.article.domain.vo;

import com.shiye.user.module.article.domain.entity.ArticleEntity;
import com.shiye.user.module.tag.domain.vo.TagVO;

import java.time.LocalDateTime;
import java.util.List;

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
        List<TagVO> tags,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ArticleSummaryVO from(ArticleEntity entity) {
        return from(entity, List.of());
    }

    public static ArticleSummaryVO from(ArticleEntity entity, List<TagVO> tags) {
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
                tags == null ? List.of() : tags,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
