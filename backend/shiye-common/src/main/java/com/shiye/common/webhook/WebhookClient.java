package com.shiye.common.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 管理端配置的 Webhook 通知。日志只记录 host，不打印完整 URL/正文。
 */
@Component
public class WebhookClient {

    private static final Logger log = LoggerFactory.getLogger(WebhookClient.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public WebhookClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public WebhookResult send(String url, Map<String, Object> payload) {
        if (url == null || url.isBlank()) {
            return new WebhookResult(false, 0, "未配置 Webhook URL");
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException ex) {
            return new WebhookResult(false, 0, "Webhook URL 非法");
        }
        if (!"http".equalsIgnoreCase(uri.getScheme())
                && !"https".equalsIgnoreCase(uri.getScheme())) {
            return new WebhookResult(false, 0, "Webhook 仅支持 http/https");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "ShiYe-Webhook/1.0")
                    .header("X-ShiYe-Event", String.valueOf(payload.getOrDefault("event", "ALERT")))
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new WebhookResult(true, response.statusCode(), "发送成功");
            }
            log.warn("webhook {} returned HTTP {}", uri.getHost(), response.statusCode());
            return new WebhookResult(false, response.statusCode(),
                    "Webhook 返回 HTTP " + response.statusCode());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new WebhookResult(false, 0, "Webhook 请求被中断");
        } catch (Exception ex) {
            log.warn("webhook {} failed: {}", uri.getHost(), ex.getMessage());
            return new WebhookResult(false, 0, "Webhook 请求失败");
        }
    }

    public record WebhookResult(boolean ok, int status, String message) {
    }
}
