package com.shiye.user.module.chat.support;

import java.util.List;

/**
 * 检索结果及 rerank 用量信息；rerank 失败时 recalled=false，回退混合检索排序。
 */
public record RetrievalResult(
        List<RetrievedChunk> chunks,
        boolean reranked,
        String rerankModel,
        int rerankTokens,
        int rerankLatencyMs
) {

    public static RetrievalResult withoutRerank(List<RetrievedChunk> chunks) {
        return new RetrievalResult(chunks, false, null, 0, 0);
    }
}
