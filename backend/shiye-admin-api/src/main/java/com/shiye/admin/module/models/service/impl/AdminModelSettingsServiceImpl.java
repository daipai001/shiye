package com.shiye.admin.module.models.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiye.admin.module.models.mapper.AdminModelConfigMapper;
import com.shiye.admin.module.models.service.AdminModelSettingsService;
import com.shiye.common.ai.ModelConfig;
import com.shiye.common.ai.ModelConfigNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class AdminModelSettingsServiceImpl implements AdminModelSettingsService {

    private static final Logger log = LoggerFactory.getLogger(AdminModelSettingsServiceImpl.class);

    private final AdminModelConfigMapper mapper;
    private final ObjectMapper objectMapper;
    private final ModelConfig defaults;

    public AdminModelSettingsServiceImpl(
            AdminModelConfigMapper mapper,
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
            @Value("${shiye.ai.rerank.model:BAAI/bge-reranker-v2-m3}") String rerankModel) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
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
    }

    @Override
    public ModelConfig current() {
        try {
            String json = mapper.findModelConfig();
            if (json == null || json.isBlank()) {
                return defaults;
            }
            ModelConfig parsed = objectMapper.readValue(json, ModelConfig.class);
            return ModelConfigNormalizer.normalize(defaults, parsed);
        } catch (Exception ex) {
            log.warn("load admin model config failed, fallback to defaults: {}", ex.getMessage());
            return defaults;
        }
    }

    @Override
    @Transactional
    public ModelConfig save(ModelConfig config, Long adminId) {
        ModelConfig normalized = ModelConfigNormalizer.normalize(defaults, config);
        try {
            mapper.upsertModelConfig(objectMapper.writeValueAsString(normalized), adminId);
            return normalized;
        } catch (Exception ex) {
            throw new IllegalStateException("保存模型配置失败", ex);
        }
    }

    @Override
    public boolean fromDatabase() {
        try {
            String json = mapper.findModelConfig();
            return json != null && !json.isBlank();
        } catch (RuntimeException ex) {
            log.warn("check model config source failed: {}", ex.getMessage());
            return false;
        }
    }
}
