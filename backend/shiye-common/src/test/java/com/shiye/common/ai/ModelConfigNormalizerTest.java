package com.shiye.common.ai;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelConfigNormalizerTest {

    private final ModelConfig defaults = new ModelConfig(
            new ModelConfig.Chat("https://api.deepseek.com/v1", "default-chat-key",
                    "deepseek-chat", 1600, 0.25,
                    new BigDecimal("2.0"), new BigDecimal("8.0")),
            new ModelConfig.Embedding("http", "https://api.siliconflow.cn/v1",
                    "default-embedding-key", "BAAI/bge-m3", 1024, 16),
            new ModelConfig.Rerank(true, "https://api.siliconflow.cn/v1",
                    "default-rerank-key", "BAAI/bge-reranker-v2-m3")
    );

    @Test
    void mergesPartialConfigWithDefaults() {
        ModelConfig parsed = new ModelConfig(
                new ModelConfig.Chat("", null, "deepseek-reasoner", 0, -1, null, null),
                new ModelConfig.Embedding("dev", "", "", "", 0, 0),
                new ModelConfig.Rerank(false, "", null, "")
        );

        ModelConfig normalized = ModelConfigNormalizer.normalize(defaults, parsed);

        assertEquals("https://api.deepseek.com/v1", normalized.chat().baseUrl());
        assertEquals("default-chat-key", normalized.chat().apiKey());
        assertEquals("deepseek-reasoner", normalized.chat().model());
        assertEquals(1600, normalized.chat().maxTokens());
        assertEquals(0.25, normalized.chat().temperature());
        assertEquals("http", normalized.embedding().provider());
        assertEquals("BAAI/bge-m3", normalized.embedding().model());
        assertEquals(1024, normalized.embedding().dimension());
        assertEquals(16, normalized.embedding().batchSize());
        assertTrue(!normalized.rerank().enabled());
        assertEquals("BAAI/bge-reranker-v2-m3", normalized.rerank().model());
    }

    @Test
    void clampsOutOfRangeValues() {
        ModelConfig parsed = new ModelConfig(
                new ModelConfig.Chat("https://example.com/v1", "", "m", 99999, 9,
                        new BigDecimal("-1"), new BigDecimal("1.5")),
                new ModelConfig.Embedding("http", "https://example.com/v1", "", "m",
                        99999, 999),
                new ModelConfig.Rerank(true, "https://example.com/v1", "", "m")
        );

        ModelConfig normalized = ModelConfigNormalizer.normalize(defaults, parsed);

        assertEquals(8192, normalized.chat().maxTokens());
        assertEquals(2.0, normalized.chat().temperature());
        assertEquals(BigDecimal.ZERO, normalized.chat().inputPrice());
        assertEquals(4096, normalized.embedding().dimension());
        assertEquals(64, normalized.embedding().batchSize());
        assertEquals("m", normalized.embedding().model());
    }
}
