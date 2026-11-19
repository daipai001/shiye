package com.shiye.common.ai;

import java.math.BigDecimal;

/**
 * 合并数据库配置与环境默认值，并做安全范围收敛。
 */
public final class ModelConfigNormalizer {

    private ModelConfigNormalizer() {
    }

    public static ModelConfig normalize(ModelConfig defaults, ModelConfig parsed) {
        ModelConfig.Chat defaultChat = defaults.chat();
        ModelConfig.Chat chat = parsed == null || parsed.chat() == null
                ? defaultChat : parsed.chat();
        ModelConfig.Chat normalizedChat = new ModelConfig.Chat(
                textOrDefault(chat.baseUrl(), defaultChat.baseUrl()),
                keyOrDefault(chat.apiKey(), defaultChat.apiKey()),
                textOrDefault(chat.model(), defaultChat.model()),
                clamp(chat.maxTokens() > 0 ? chat.maxTokens() : defaultChat.maxTokens(), 64, 8192),
                clamp(chat.temperature() >= 0 ? chat.temperature() : defaultChat.temperature(),
                        0, 2),
                priceOrZero(chat.inputPrice(), defaultChat.inputPrice()),
                priceOrZero(chat.outputPrice(), defaultChat.outputPrice())
        );

        ModelConfig.Embedding defaultEmbedding = defaults.embedding();
        ModelConfig.Embedding embedding = parsed == null || parsed.embedding() == null
                ? defaultEmbedding : parsed.embedding();
        ModelConfig.Embedding normalizedEmbedding = new ModelConfig.Embedding(
                defaultEmbedding.provider(),
                textOrDefault(embedding.baseUrl(), defaultEmbedding.baseUrl()),
                keyOrDefault(embedding.apiKey(), defaultEmbedding.apiKey()),
                textOrDefault(embedding.model(), defaultEmbedding.model()),
                clamp(embedding.dimension() > 0 ? embedding.dimension() : defaultEmbedding.dimension(),
                        8, 4096),
                clamp(embedding.batchSize() > 0 ? embedding.batchSize() : defaultEmbedding.batchSize(),
                        1, 64)
        );

        ModelConfig.Rerank defaultRerank = defaults.rerank();
        ModelConfig.Rerank rerank = parsed == null || parsed.rerank() == null
                ? defaultRerank : parsed.rerank();
        ModelConfig.Rerank normalizedRerank = new ModelConfig.Rerank(
                rerank.enabled(),
                textOrDefault(rerank.baseUrl(), defaultRerank.baseUrl()),
                keyOrDefault(rerank.apiKey(), defaultRerank.apiKey()),
                textOrDefault(rerank.model(), defaultRerank.model())
        );

        return new ModelConfig(normalizedChat, normalizedEmbedding, normalizedRerank);
    }

    private static String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String keyOrDefault(String value, String fallback) {
        return value == null ? fallback : value.trim();
    }

    private static BigDecimal priceOrZero(BigDecimal value, BigDecimal fallback) {
        BigDecimal effective = value == null ? fallback : value;
        if (effective == null || effective.signum() < 0) {
            return BigDecimal.ZERO;
        }
        return effective;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }
}
