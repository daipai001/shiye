package com.shiye.admin.module.models.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiye.admin.module.models.domain.vo.AdminModelConfigVO;
import com.shiye.admin.module.models.domain.vo.ModelTestVO;
import com.shiye.admin.module.models.service.AdminModelService;
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
import java.util.Locale;
import java.util.Map;

@Service
public class AdminModelServiceImpl implements AdminModelService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private final String chatBaseUrl;
    private final String chatApiKey;
    private final String chatModel;
    private final int chatMaxTokens;
    private final double chatTemperature;
    private final BigDecimal chatInputPrice;
    private final BigDecimal chatOutputPrice;

    private final String embeddingProvider;
    private final String embeddingBaseUrl;
    private final String embeddingApiKey;
    private final String embeddingModel;
    private final int embeddingDimension;
    private final int embeddingBatchSize;

    private final long globalMonthlyTokens;
    private final BigDecimal globalMonthlyCost;
    private final long defaultUserTokens;
    private final BigDecimal defaultUserCost;

    public AdminModelServiceImpl(
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
            @Value("${shiye.usage.global-monthly-tokens:2000000}") long globalMonthlyTokens,
            @Value("${shiye.usage.global-monthly-cost:20.00}") BigDecimal globalMonthlyCost,
            @Value("${shiye.registration.default-token-quota:200000}") long defaultUserTokens,
            @Value("${shiye.registration.default-cost-quota:5.000000}") BigDecimal defaultUserCost) {
        this.objectMapper = objectMapper;
        this.chatBaseUrl = trimSlash(chatBaseUrl);
        this.chatApiKey = safe(chatApiKey);
        this.chatModel = chatModel;
        this.chatMaxTokens = chatMaxTokens;
        this.chatTemperature = chatTemperature;
        this.chatInputPrice = chatInputPrice == null ? BigDecimal.ZERO : chatInputPrice;
        this.chatOutputPrice = chatOutputPrice == null ? BigDecimal.ZERO : chatOutputPrice;
        this.embeddingProvider = embeddingProvider == null ? "dev" : embeddingProvider;
        this.embeddingBaseUrl = trimSlash(embeddingBaseUrl);
        this.embeddingApiKey = safe(embeddingApiKey);
        this.embeddingModel = embeddingModel;
        this.embeddingDimension = embeddingDimension;
        this.embeddingBatchSize = embeddingBatchSize;
        this.globalMonthlyTokens = globalMonthlyTokens;
        this.globalMonthlyCost = globalMonthlyCost;
        this.defaultUserTokens = defaultUserTokens;
        this.defaultUserCost = defaultUserCost;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public AdminModelConfigVO config() {
        return new AdminModelConfigVO(
                new AdminModelConfigVO.ChatModelVO(
                        "DeepSeek",
                        chatBaseUrl,
                        chatModel,
                        mask(chatApiKey),
                        !chatApiKey.isEmpty(),
                        chatMaxTokens,
                        chatTemperature,
                        chatInputPrice,
                        chatOutputPrice,
                        "CNY"
                ),
                new AdminModelConfigVO.EmbeddingModelVO(
                        embeddingProvider,
                        embeddingBaseUrl,
                        embeddingModel,
                        mask(embeddingApiKey),
                        "dev".equalsIgnoreCase(embeddingProvider) || !embeddingApiKey.isEmpty(),
                        embeddingDimension,
                        embeddingBatchSize,
                        BigDecimal.ZERO,
                        "CNY"
                ),
                new AdminModelConfigVO.BudgetVO(
                        globalMonthlyTokens,
                        globalMonthlyCost,
                        defaultUserTokens,
                        defaultUserCost
                )
        );
    }

    @Override
    public ModelTestVO test(String type) {
        long started = System.currentTimeMillis();
        try {
            if ("CHAT".equalsIgnoreCase(type)) {
                if (chatApiKey.isEmpty()) {
                    return new ModelTestVO(false, 0, chatModel, "未配置 DeepSeek API Key");
                }
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(chatBaseUrl + "/models"))
                        .timeout(Duration.ofSeconds(20))
                        .header("Authorization", "Bearer " + chatApiKey)
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());
                int latency = (int) (System.currentTimeMillis() - started);
                if (response.statusCode() != 200) {
                    return new ModelTestVO(false, latency, chatModel,
                            "DeepSeek 返回 HTTP " + response.statusCode());
                }
                return new ModelTestVO(true, latency, chatModel, "连接成功");
            }

            if ("EMBEDDING".equalsIgnoreCase(type)) {
                if ("dev".equalsIgnoreCase(embeddingProvider)) {
                    return new ModelTestVO(true, 0, embeddingModel,
                            "当前为 dev 联调向量，不是真实 BGE-M3；切换 provider=http 后测试真实连接");
                }
                if (embeddingApiKey.isEmpty()) {
                    return new ModelTestVO(false, 0, embeddingModel, "未配置 Embedding API Key");
                }
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("model", embeddingModel);
                body.put("input", List.of("拾页连接测试"));
                body.put("encoding_format", "float");
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(embeddingBaseUrl + "/embeddings"))
                        .timeout(Duration.ofSeconds(30))
                        .header("Authorization", "Bearer " + embeddingApiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                objectMapper.writeValueAsString(body)))
                        .build();
                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());
                int latency = (int) (System.currentTimeMillis() - started);
                if (response.statusCode() != 200) {
                    return new ModelTestVO(false, latency, embeddingModel,
                            "Embedding 返回 HTTP " + response.statusCode());
                }
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode embedding = root.path("data").path(0).path("embedding");
                if (!embedding.isArray() || embedding.size() != embeddingDimension) {
                    return new ModelTestVO(false, latency, embeddingModel,
                            "返回维度不是 " + embeddingDimension);
                }
                return new ModelTestVO(true, latency, embeddingModel,
                        "连接成功，维度 " + embedding.size());
            }

            return new ModelTestVO(false, 0, null, "不支持的测试类型");
        } catch (Exception ex) {
            int latency = (int) (System.currentTimeMillis() - started);
            return new ModelTestVO(false, latency, null, "测试失败：" + ex.getMessage());
        }
    }

    private String trimSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String mask(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return key.length() <= 8 ? "****" : key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
