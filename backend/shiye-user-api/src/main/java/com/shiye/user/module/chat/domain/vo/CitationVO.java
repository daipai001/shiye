package com.shiye.user.module.chat.domain.vo;

import com.shiye.user.module.chat.domain.entity.CitationEntity;

import java.math.BigDecimal;

public record CitationVO(
        int rank,
        String articleId,
        String chunkId,
        String title,
        String quote,
        BigDecimal score,
        int charStart,
        int charEnd
) {

    public static CitationVO from(CitationEntity entity) {
        return new CitationVO(
                entity.getRank() == null ? 0 : entity.getRank(),
                String.valueOf(entity.getArticleId()),
                String.valueOf(entity.getChunkId()),
                entity.getTitle(),
                entity.getQuote(),
                entity.getScore(),
                entity.getCharStart() == null ? 0 : entity.getCharStart(),
                entity.getCharEnd() == null ? 0 : entity.getCharEnd()
        );
    }
}
