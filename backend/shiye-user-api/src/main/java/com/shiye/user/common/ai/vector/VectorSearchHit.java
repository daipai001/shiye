package com.shiye.user.common.ai.vector;

/**
 * RediSearch KNN 召回的一条结果。
 */
public record VectorSearchHit(
        long articleId,
        long chunkId,
        String content,
        int charStart,
        int charEnd,
        double score
) {
}
