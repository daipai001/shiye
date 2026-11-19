package com.shiye.user.common.ai.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiye.common.ai.ModelConfig;
import com.shiye.user.common.ai.config.RuntimeModelConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 /embeddings 客户端，默认目标是硅基流动 BGE-M3。
 * 不打印 API Key，也不把完整响应写入日志。
 */
@Component
@ConditionalOnProperty(name = "shiye.ai.embedding.provider", havingValue = "http")
public class HttpEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(HttpEmbeddingClient.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final RuntimeModelConfigService modelConfig;
    private final long timeoutMs;

    public HttpEmbeddingClient(
            ObjectMapper objectMapper,
            RuntimeModelConfigService modelConfig,
            @Value("${shiye.ai.embedding.timeout-ms:30000}") long timeoutMs) {
        this.objectMapper = objectMapper;
        this.modelConfig = modelConfig;
        this.timeoutMs = Math.max(1000, timeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        ModelConfig.Embedding config = modelConfig.embedding();
        if (config.apiKey().isEmpty()) {
            throw new EmbeddingException("EMBEDDING_NOT_CONFIGURED", "Embedding API Key 未配置");
        }
        if (texts.isEmpty()) {
            return List.of();
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", config.model());
            body.put("input", texts);
            body.put("encoding_format", "float");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl() + "/embeddings"))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new EmbeddingException("EMBEDDING_HTTP_ERROR",
                        "Embedding 服务返回 HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode data = root.path("data");
            if (!data.isArray() || data.size() != texts.size()) {
                throw new EmbeddingException("EMBEDDING_BAD_RESPONSE", "Embedding 返回数量不匹配");
            }

            List<float[]> vectors = new ArrayList<>(data.size());
            for (JsonNode item : data) {
                JsonNode embedding = item.path("embedding");
                if (!embedding.isArray() || embedding.size() != config.dimension()) {
                    throw new EmbeddingException("EMBEDDING_BAD_DIMENSION",
                            "Embedding 维度不是配置的 " + config.dimension());
                }
                float[] vector = new float[config.dimension()];
                for (int i = 0; i < config.dimension(); i++) {
                    vector[i] = (float) embedding.get(i).asDouble();
                }
                vectors.add(vector);
            }
            return vectors;
        } catch (EmbeddingException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new EmbeddingException("EMBEDDING_FAILED", "Embedding 请求被中断", ex);
        } catch (Exception ex) {
            log.warn("embedding request failed: {}", ex.getMessage());
            throw new EmbeddingException("EMBEDDING_FAILED", "Embedding 请求失败", ex);
        }
    }

    @Override
    public String model() {
        return modelConfig.embedding().model();
    }

    @Override
    public int dimension() {
        return modelConfig.embedding().dimension();
    }
}
