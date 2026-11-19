package com.shiye.user.common.ai.rerank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BGE-reranker（OpenAI 兼容 /rerank）客户端。
 * 只返回排序结果和 token 用量，不打印 key/正文。
 */
@Service
public class RerankService {

    private static final Logger log = LoggerFactory.getLogger(RerankService.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final boolean enabled;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int topN;
    private final long timeoutMs;

    public RerankService(
            ObjectMapper objectMapper,
            @Value("${shiye.ai.rerank.enabled:false}") boolean enabled,
            @Value("${shiye.ai.rerank.base-url:https://api.siliconflow.cn/v1}") String baseUrl,
            @Value("${shiye.ai.rerank.api-key:}") String apiKey,
            @Value("${shiye.ai.rerank.model:BAAI/bge-reranker-v2-m3}") String model,
            @Value("${shiye.ai.rerank.top-n:20}") int topN,
            @Value("${shiye.ai.rerank.timeout-ms:20000}") long timeoutMs) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.topN = Math.max(1, Math.min(topN, 100));
        this.timeoutMs = Math.max(1000, timeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean isEnabled() {
        return enabled && !apiKey.isEmpty();
    }

    public String model() {
        return model;
    }

    public RerankResult rerank(String query, List<String> documents) {
        if (!isEnabled()) {
            throw new RerankException("RERANK_NOT_CONFIGURED", "Rerank 未启用或未配置 API Key");
        }
        if (documents == null || documents.isEmpty()) {
            return new RerankResult(List.of(), 0, 0);
        }
        long started = System.currentTimeMillis();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("query", query);
            body.put("documents", documents);
            body.put("top_n", Math.min(topN, documents.size()));
            body.put("return_documents", false);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/rerank"))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            int latency = (int) (System.currentTimeMillis() - started);
            if (response.statusCode() != 200) {
                throw new RerankException("RERANK_HTTP_ERROR",
                        "Rerank 返回 HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode results = root.path("results");
            if (!results.isArray()) {
                throw new RerankException("RERANK_BAD_RESPONSE", "Rerank 返回格式不正确");
            }
            List<RerankResult.Item> items = new ArrayList<>();
            for (JsonNode result : results) {
                int index = result.path("index").asInt(-1);
                double score = result.path("relevance_score").asDouble(0);
                if (index >= 0 && index < documents.size()) {
                    items.add(new RerankResult.Item(index, score));
                }
            }
            items.sort(Comparator.comparingDouble(RerankResult.Item::score).reversed());
            int tokens = root.path("meta").path("tokens").path("input_tokens").asInt(0);
            return new RerankResult(items, tokens, latency);
        } catch (RerankException ex) {
            throw ex;
        } catch (HttpTimeoutException ex) {
            throw new RerankException("RERANK_TIMEOUT", "Rerank 请求超时", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RerankException("RERANK_INTERRUPTED", "Rerank 请求被中断", ex);
        } catch (Exception ex) {
            log.warn("rerank request failed: {}", ex.getMessage());
            throw new RerankException("RERANK_FAILED", "Rerank 请求失败", ex);
        }
    }
}
