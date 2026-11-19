package com.shiye.admin.module.models.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiye.admin.module.models.domain.dto.AdminModelUpdateRequest;
import com.shiye.admin.module.models.domain.vo.AdminModelConfigVO;
import com.shiye.admin.module.models.domain.vo.ModelTestVO;
import com.shiye.admin.module.models.service.AdminModelService;
import com.shiye.admin.module.models.service.AdminModelSettingsService;
import com.shiye.admin.module.settings.service.AdminSettingsService;
import com.shiye.common.ai.ModelConfig;
import com.shiye.common.settings.SystemSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminModelServiceImpl implements AdminModelService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final AdminModelSettingsService modelSettingsService;
    private final AdminSettingsService systemSettingsService;

    public AdminModelServiceImpl(
            ObjectMapper objectMapper,
            AdminModelSettingsService modelSettingsService,
            AdminSettingsService systemSettingsService) {
        this.objectMapper = objectMapper;
        this.modelSettingsService = modelSettingsService;
        this.systemSettingsService = systemSettingsService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public AdminModelConfigVO config() {
        ModelConfig config = modelSettingsService.current();
        ModelConfig.Chat chat = config.chat();
        ModelConfig.Embedding embedding = config.embedding();
        ModelConfig.Rerank rerank = config.rerank();
        SystemSettings system = systemSettingsService.current();
        return new AdminModelConfigVO(
                new AdminModelConfigVO.ChatModelVO(
                        "DeepSeek",
                        chat.baseUrl(),
                        chat.model(),
                        mask(chat.apiKey()),
                        !chat.apiKey().isEmpty(),
                        chat.maxTokens(),
                        chat.temperature(),
                        chat.inputPrice(),
                        chat.outputPrice(),
                        "CNY"
                ),
                new AdminModelConfigVO.EmbeddingModelVO(
                        embedding.provider(),
                        embedding.baseUrl(),
                        embedding.model(),
                        mask(embedding.apiKey()),
                        "dev".equalsIgnoreCase(embedding.provider()) || !embedding.apiKey().isEmpty(),
                        embedding.dimension(),
                        embedding.batchSize(),
                        BigDecimal.ZERO,
                        "CNY"
                ),
                new AdminModelConfigVO.RerankModelVO(
                        rerank.enabled(),
                        rerank.baseUrl(),
                        rerank.model(),
                        mask(rerank.apiKey()),
                        rerank.enabled() && !rerank.apiKey().isEmpty()
                ),
                new AdminModelConfigVO.BudgetVO(
                        system.globalMonthlyTokens(),
                        system.globalMonthlyCost(),
                        system.defaultMonthlyTokens(),
                        system.defaultMonthlyCost()
                ),
                modelSettingsService.fromDatabase() ? "DATABASE" : "ENV"
        );
    }

    @Override
    public AdminModelConfigVO update(AdminModelUpdateRequest request, long adminId) {
        ModelConfig current = modelSettingsService.current();
        String chatKey = keepKey(request.chat().apiKey(), current.chat().apiKey());
        String embeddingKey = keepKey(request.embedding().apiKey(), current.embedding().apiKey());
        String rerankKey = keepKey(request.rerank().apiKey(), current.rerank().apiKey());
        String rerankBaseUrl = text(request.rerank().baseUrl(), current.rerank().baseUrl());
        String rerankModel = text(request.rerank().model(), current.rerank().model());

        ModelConfig next = new ModelConfig(
                new ModelConfig.Chat(
                        request.chat().baseUrl().trim(),
                        chatKey,
                        request.chat().model().trim(),
                        request.chat().maxTokens(),
                        request.chat().temperature(),
                        request.chat().inputPrice(),
                        request.chat().outputPrice()
                ),
                new ModelConfig.Embedding(
                        current.embedding().provider(),
                        request.embedding().baseUrl().trim(),
                        embeddingKey,
                        request.embedding().model().trim(),
                        request.embedding().dimension(),
                        request.embedding().batchSize()
                ),
                new ModelConfig.Rerank(
                        request.rerank().enabled(),
                        rerankBaseUrl,
                        rerankKey,
                        rerankModel
                )
        );
        modelSettingsService.save(next, adminId);
        return config();
    }

    @Override
    public ModelTestVO test(String type) {
        ModelConfig config = modelSettingsService.current();
        long started = System.currentTimeMillis();
        try {
            if ("CHAT".equalsIgnoreCase(type)) {
                return testChat(config.chat(), started);
            }
            if ("EMBEDDING".equalsIgnoreCase(type)) {
                return testEmbedding(config.embedding(), started);
            }
            if ("RERANK".equalsIgnoreCase(type)) {
                return testRerank(config.rerank(), started);
            }
            return new ModelTestVO(false, 0, null, "不支持的测试类型");
        } catch (Exception ex) {
            int latency = (int) (System.currentTimeMillis() - started);
            return new ModelTestVO(false, latency, null, "测试失败：" + ex.getMessage());
        }
    }

    private ModelTestVO testChat(ModelConfig.Chat config, long started) throws Exception {
        if (config.apiKey().isEmpty()) {
            return new ModelTestVO(false, 0, config.model(), "未配置 DeepSeek API Key");
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(trimSlash(config.baseUrl()) + "/models"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + config.apiKey())
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());
        int latency = (int) (System.currentTimeMillis() - started);
        if (response.statusCode() != 200) {
            return new ModelTestVO(false, latency, config.model(),
                    "DeepSeek 返回 HTTP " + response.statusCode());
        }
        return new ModelTestVO(true, latency, config.model(), "连接成功");
    }

    private ModelTestVO testEmbedding(ModelConfig.Embedding config, long started) throws Exception {
        if ("dev".equalsIgnoreCase(config.provider())) {
            return new ModelTestVO(true, 0, config.model(),
                    "当前为 dev 联调向量，不是真实 BGE-M3；切换 provider=http 后测试真实连接");
        }
        if (config.apiKey().isEmpty()) {
            return new ModelTestVO(false, 0, config.model(), "未配置 Embedding API Key");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.model());
        body.put("input", List.of("拾页连接测试"));
        body.put("encoding_format", "float");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(trimSlash(config.baseUrl()) + "/embeddings"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());
        int latency = (int) (System.currentTimeMillis() - started);
        if (response.statusCode() != 200) {
            return new ModelTestVO(false, latency, config.model(),
                    "Embedding 返回 HTTP " + response.statusCode());
        }
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode embedding = root.path("data").path(0).path("embedding");
        if (!embedding.isArray() || embedding.size() != config.dimension()) {
            return new ModelTestVO(false, latency, config.model(),
                    "返回维度不是 " + config.dimension());
        }
        return new ModelTestVO(true, latency, config.model(),
                "连接成功，维度 " + embedding.size());
    }

    private ModelTestVO testRerank(ModelConfig.Rerank config, long started) throws Exception {
        if (!config.enabled()) {
            return new ModelTestVO(false, 0, config.model(), "Rerank 当前未启用");
        }
        if (config.apiKey().isEmpty()) {
            return new ModelTestVO(false, 0, config.model(), "未配置 Rerank API Key");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.model());
        body.put("query", "RAG 切片重叠比例");
        body.put("documents", List.of("RAG 切片建议保留 10% 到 15% 重叠。", "这段文本无关。"));
        body.put("top_n", 2);
        body.put("return_documents", false);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(trimSlash(config.baseUrl()) + "/rerank"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());
        int latency = (int) (System.currentTimeMillis() - started);
        if (response.statusCode() != 200) {
            return new ModelTestVO(false, latency, config.model(),
                    "Rerank 返回 HTTP " + response.statusCode());
        }
        JsonNode results = objectMapper.readTree(response.body()).path("results");
        if (!results.isArray() || results.isEmpty()) {
            return new ModelTestVO(false, latency, config.model(), "Rerank 返回结果为空");
        }
        return new ModelTestVO(true, latency, config.model(), "连接成功");
    }

    private String keepKey(String input, String current) {
        return input == null || input.isBlank() ? current : input.trim();
    }

    private String text(String input, String fallback) {
        return input == null || input.isBlank() ? fallback : input.trim();
    }

    private String trimSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private String mask(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return key.length() <= 8 ? "****" : key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
