package com.shiye.admin.module.models.domain.vo;

import java.math.BigDecimal;

public record AdminModelConfigVO(
        ChatModelVO chat,
        EmbeddingModelVO embedding,
        RerankModelVO rerank,
        BudgetVO budget,
        String source
) {

    public record ChatModelVO(
            String provider,
            String baseUrl,
            String model,
            String apiKeyMasked,
            boolean configured,
            int maxTokens,
            double temperature,
            BigDecimal inputPrice,
            BigDecimal outputPrice,
            String currency
    ) {
    }

    public record EmbeddingModelVO(
            String provider,
            String baseUrl,
            String model,
            String apiKeyMasked,
            boolean configured,
            int dimension,
            int batchSize,
            BigDecimal inputPrice,
            String currency
    ) {
    }

    public record RerankModelVO(
            boolean enabled,
            String baseUrl,
            String model,
            String apiKeyMasked,
            boolean configured
    ) {
    }

    public record BudgetVO(
            long globalMonthlyTokens,
            BigDecimal globalMonthlyCost,
            long defaultUserTokens,
            BigDecimal defaultUserCost
    ) {
    }
}
