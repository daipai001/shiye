package com.shiye.user.module.chat.domain.vo;

public record ChatSseCitation(
        int index,
        String articleId,
        String chunkId,
        String title,
        String quote,
        double score,
        int charStart,
        int charEnd
) {
}
