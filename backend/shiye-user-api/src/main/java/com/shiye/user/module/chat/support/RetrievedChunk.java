package com.shiye.user.module.chat.support;

public record RetrievedChunk(
        long chunkId,
        long articleId,
        String title,
        String url,
        String content,
        double score,
        int charStart,
        int charEnd
) {
}
