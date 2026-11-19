package com.shiye.user.common.ai.rerank;

import java.util.List;

public record RerankResult(
        List<Item> items,
        int inputTokens,
        int latencyMs
) {

    public record Item(int index, double score) {
    }
}
