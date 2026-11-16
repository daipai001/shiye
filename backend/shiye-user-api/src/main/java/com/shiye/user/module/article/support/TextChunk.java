package com.shiye.user.module.article.support;

public record TextChunk(
        int index,
        String content,
        int charStart,
        int charEnd,
        int tokenCount,
        String contentHash
) {
}
