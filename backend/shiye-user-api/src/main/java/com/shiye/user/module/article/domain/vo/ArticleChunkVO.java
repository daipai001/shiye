package com.shiye.user.module.article.domain.vo;

import com.shiye.user.module.article.domain.entity.ArticleChunkEntity;

public record ArticleChunkVO(
        String id,
        Integer chunkIndex,
        String content,
        Integer tokenCount,
        Integer charStart,
        Integer charEnd,
        String embeddingModel,
        Integer embeddingDim,
        String vectorId
) {

    public static ArticleChunkVO from(ArticleChunkEntity entity) {
        return new ArticleChunkVO(
                String.valueOf(entity.getId()),
                entity.getChunkIndex(),
                entity.getContent(),
                entity.getTokenCount(),
                entity.getCharStart(),
                entity.getCharEnd(),
                entity.getEmbeddingModel(),
                entity.getEmbeddingDim(),
                entity.getVectorId()
        );
    }
}
