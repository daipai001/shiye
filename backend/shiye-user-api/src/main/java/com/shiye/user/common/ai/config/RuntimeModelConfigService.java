package com.shiye.user.common.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiye.common.ai.ModelConfig;
import com.shiye.common.ai.ModelConfigNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * user-api 运行时模型配置：优先读 system_settings，5 秒缓存，失败回退环境变量默认值。
 */
@Service
public class RuntimeModelConfigService {

    private static final Logger log = LoggerFactory.getLogger(RuntimeModelConfigService.class);

    private final ModelConfigMapper mapper;
    private final ObjectMapper objectMapper;
    private final ModelConfig defaults;
    private final long refreshMs;

    private volatile ModelConfig cached;
    private volatile long loadedAt;

    public RuntimeModelConfigService(
            ModelConfigMapper mapper,
            ObjectMapper objectMapper,
            @Value("${shiye.ai.chat.base-url:https://api.deepseek.com/v1}") String chatBaseUrl,
            @Value("${shiye.ai.chat.api-key:}") String chatApiKey,
            @Value("${shiye.ai.chat.model:deepseek-chat}") String chatModel,
            @Value("${shiye.ai.chat.max-tokens:1600}") int chatMaxTokens,
            @Value("${shiye.ai.chat.temperature:0.25}") double chatTemperature,
            @Value("${shiye.ai.chat.input-price:2.0}") BigDecimal chatInputPrice,
            @Value("${shiye.ai.chat.output-price:8.0}") BigDecimal chatOutputPrice,
            @Value("${shiye.ai.embedding.provider:dev}") String embeddingProvider,
            @Value("${shiye.ai.embedding.base-url:https://api.siliconflow.cn/v1}") String embeddingBaseUrl,
            @Value("${shiye.ai.embedding.api-key:}") String embeddingApiKey,
            @Value("${shiye.ai.embedding.model:BAAI/bge-m3}") String embeddingModel,
            @Value("${shiye.ai.embedding.dimension:1024}") int embeddingDimension,
            @Value("${shiye.ai.embedding.batch-size:16}") int embeddingBatchSize,
            @Value("${shiye.ai.rerank.enabled:false}") boolean rerankEnabled,
            @Value("${shiye.ai.rerank.base-url:https://api.siliconflow.cn/v1}") String rerankBaseUrl,
            @Value("${shiye.ai.rerank.api-key:}") String rerankApiKey,
            @Value("${shiye.ai.rerank.model:BAAI/bge-reranker-v2-m3}") String rerankModel,
            @Value("${shiye.ai.config-refresh-ms:5000}") long refreshMs) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.refreshMs = Math.max(1000, refreshMs);
        this.defaults = new ModelConfig(
                new ModelConfig.Chat(
                        chatBaseUrl, chatApiKey, chatModel, chatMaxTokens, chatTemperature,
                        chatInputPrice == null ? BigDecimal.ZERO : chatInputPrice,
                        chatOutputPrice == null ? BigDecimal.ZERO : chatOutputPrice),
                new ModelConfig.Embedding(
                        embeddingProvider, embeddingBaseUrl, embeddingApiKey, embeddingModel,
                        embeddingDimension, embeddingBatchSize),
                new ModelConfig.Rerank(
                        rerankEnabled, rerankBaseUrl, rerankApiKey, rerankModel)
        );
        this.cached = defaults;
    }

    public ModelConfig current() {
        long now = System.currentTimeMillis();
        if (now - loadedAt < refreshMs) {
            return cached;
        }
        synchronized (this) {
            if (now - loadedAt < refreshMs) {
                return cached;
            }
            cached = load();
            loadedAt = now;
            return cached;
        }
    }

    public ModelConfig.Chat chat() {
        return current().chat();
    }

    public ModelConfig.Embedding embedding() {
        return current().embedding();
    }

    public ModelConfig.Rerank rerank() {
        return current().rerank();
    }

    private ModelConfig load() {
        try {
            String json = mapper.findModelConfig();
            if (json == null || json.isBlank()) {
                return defaults;
            }
            ModelConfig parsed = objectMapper.readValue(json, ModelConfig.class);
            return ModelConfigNormalizer.normalize(defaults, parsed);
        } catch (Exception ex) {
            log.warn("load runtime model config failed, fallback to defaults: {}", ex.getMessage());
            return defaults;
        }
    }
}
