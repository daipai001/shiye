package com.shiye.common.ai;

import java.math.BigDecimal;

/**
 * 运行时 AI 模型配置。数据库只存覆盖值，读取时与环境默认值合并。
 */
public record ModelConfig(
        Chat chat,
        Embedding embedding,
        Rerank rerank
) {

    public record Chat(
            String baseUrl,
            String apiKey,
            String model,
            int maxTokens,
            double temperature,
            BigDecimal inputPrice,
            BigDecimal outputPrice
    ) {
    }

    public record Embedding(
            String provider,
            String baseUrl,
            String apiKey,
            String model,
            int dimension,
            int batchSize
    ) {
    }

    public record Rerank(
            boolean enabled,
            String baseUrl,
            String apiKey,
            String model
    ) {
    }
}
