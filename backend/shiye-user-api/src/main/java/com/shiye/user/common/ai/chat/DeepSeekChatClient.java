package com.shiye.user.common.ai.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiye.common.ai.ModelConfig;
import com.shiye.user.common.ai.config.RuntimeModelConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * DeepSeek OpenAI 兼容流式客户端。只暴露增量文本与 usage，不打印 Key 和完整响应。
 */
@Component
public class DeepSeekChatClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekChatClient.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final RuntimeModelConfigService modelConfig;
    private final long timeoutMs;

    public DeepSeekChatClient(
            ObjectMapper objectMapper,
            RuntimeModelConfigService modelConfig,
            @Value("${shiye.ai.chat.timeout-ms:45000}") long timeoutMs) {
        this.objectMapper = objectMapper;
        this.modelConfig = modelConfig;
        this.timeoutMs = Math.max(5000, timeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public ChatCompletionResult stream(List<ChatMessage> messages,
                                       Consumer<String> onDelta,
                                       BooleanSupplier cancelled) {
        ModelConfig.Chat config = modelConfig.chat();
        if (config.apiKey().isEmpty()) {
            throw new ChatException("AI_NOT_CONFIGURED", "DeepSeek API Key 未配置");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.model());
        body.put("messages", toPayload(messages));
        body.put("stream", true);
        body.put("temperature", config.temperature());
        body.put("max_tokens", config.maxTokens());
        body.put("stream_options", Map.of("include_usage", true));

        HttpResponse<InputStream> response = send(body, config);
        if (response.statusCode() == 400) {
            body.remove("stream_options");
            response = send(body, config);
        }
        if (response.statusCode() != 200) {
            throw new ChatException("AI_UNAVAILABLE", "DeepSeek 返回 HTTP " + response.statusCode());
        }

        int promptTokens = 0;
        int completionTokens = 0;
        int totalTokens = 0;
        boolean estimated = false;
        String finishReason = null;
        StringBuilder completion = new StringBuilder();

        try (InputStream input = response.body();
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (cancelled != null && cancelled.getAsBoolean()) {
                    throw new ChatException("ABORTED", "客户端已断开");
                }
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.isEmpty()) {
                    continue;
                }
                if ("[DONE]".equals(data)) {
                    break;
                }
                JsonNode root = objectMapper.readTree(data);
                JsonNode usage = root.path("usage");
                if (!usage.isMissingNode() && !usage.isNull()) {
                    promptTokens = usage.path("prompt_tokens").asInt(promptTokens);
                    completionTokens = usage.path("completion_tokens").asInt(completionTokens);
                    totalTokens = usage.path("total_tokens").asInt(totalTokens);
                }
                JsonNode choices = root.path("choices");
                if (choices.isArray() && !choices.isEmpty()) {
                    JsonNode choice = choices.get(0);
                    String delta = choice.path("delta").path("content").asText("");
                    if (!delta.isEmpty()) {
                        completion.append(delta);
                        onDelta.accept(delta);
                    }
                    String reason = choice.path("finish_reason").asText(null);
                    if (reason != null && !reason.isBlank()) {
                        finishReason = reason;
                    }
                }
            }
        } catch (ChatException ex) {
            throw ex;
        } catch (HttpTimeoutException ex) {
            throw new ChatException("AI_TIMEOUT", "DeepSeek 响应超时", ex);
        } catch (Exception ex) {
            throw new ChatException("AI_UNAVAILABLE", "DeepSeek 流式响应中断", ex);
        }

        if (totalTokens == 0) {
            estimated = true;
            promptTokens = estimateTokens(messages);
            completionTokens = estimateTokens(completion.toString());
            totalTokens = promptTokens + completionTokens;
        }
        log.debug("deepseek stream finished, model={}, tokens={}, estimated={}",
                config.model(), totalTokens, estimated);
        return new ChatCompletionResult(promptTokens, completionTokens, totalTokens,
                estimated, finishReason);
    }

    public String model() {
        return modelConfig.chat().model();
    }

    public String resolveModel(String modelOverride) {
        return modelOverride == null || modelOverride.isBlank()
                ? modelConfig.chat().model() : modelOverride.trim();
    }

    public boolean isConfigured() {
        return !modelConfig.chat().apiKey().isEmpty();
    }

    /**
     * 非流式补全，用于摘要/标签等后台任务；jsonMode 失败时自动去掉 response_format 重试。
     */
    public ChatCompletion complete(List<ChatMessage> messages, int maxCompletionTokens,
                                   boolean jsonMode) {
        return complete(messages, maxCompletionTokens, jsonMode, null);
    }

    /**
     * 非流式补全，可指定模型覆盖值；为空时使用运行时 Chat 模型。
     */
    public ChatCompletion complete(List<ChatMessage> messages, int maxCompletionTokens,
                                   boolean jsonMode, String modelOverride) {
        ModelConfig.Chat config = modelConfig.chat();
        if (config.apiKey().isEmpty()) {
            throw new ChatException("AI_NOT_CONFIGURED", "DeepSeek API Key 未配置");
        }
        int safeMaxTokens = Math.max(64, maxCompletionTokens);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", resolveModel(modelOverride));
        body.put("messages", toPayload(messages));
        body.put("stream", false);
        body.put("temperature", 0.2);
        body.put("max_tokens", safeMaxTokens);
        if (jsonMode) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        HttpResponse<String> response = sendJson(body, config);
        if (jsonMode && response.statusCode() == 400) {
            body.remove("response_format");
            response = sendJson(body, config);
        }
        if (response.statusCode() != 200) {
            throw new ChatException("AI_UNAVAILABLE",
                    "DeepSeek 返回 HTTP " + response.statusCode());
        }

        try {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choice = root.path("choices").path(0);
            String content = choice.path("message").path("content").asText("");
            String finishReason = choice.path("finish_reason").asText(null);
            JsonNode usage = root.path("usage");
            int promptTokens = usage.path("prompt_tokens").asInt(0);
            int completionTokens = usage.path("completion_tokens").asInt(0);
            int totalTokens = usage.path("total_tokens").asInt(0);
            boolean estimated = false;
            if (totalTokens == 0) {
                estimated = true;
                promptTokens = estimateTokens(messages);
                completionTokens = estimateTokens(content);
                totalTokens = promptTokens + completionTokens;
            }
            log.debug("deepseek completion finished, model={}, tokens={}, estimated={}",
                    config.model(), totalTokens, estimated);
            return new ChatCompletion(content, promptTokens, completionTokens,
                    totalTokens, estimated, finishReason);
        } catch (ChatException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ChatException("AI_BAD_RESPONSE", "DeepSeek 返回格式不正确", ex);
        }
    }

    public BigDecimal inputPrice() {
        return modelConfig.chat().inputPrice();
    }

    public BigDecimal outputPrice() {
        return modelConfig.chat().outputPrice();
    }

    private HttpResponse<InputStream> send(Map<String, Object> body, ModelConfig.Chat config) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl() + "/chat/completions"))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body)))
                    .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (HttpTimeoutException ex) {
            throw new ChatException("AI_TIMEOUT", "DeepSeek 连接超时", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ChatException("AI_UNAVAILABLE", "DeepSeek 请求被中断", ex);
        } catch (Exception ex) {
            log.warn("deepseek request failed: {}", ex.getMessage());
            throw new ChatException("AI_UNAVAILABLE", "DeepSeek 请求失败", ex);
        }
    }

    private HttpResponse<String> sendJson(Map<String, Object> body, ModelConfig.Chat config) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl() + "/chat/completions"))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body)))
                    .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException ex) {
            throw new ChatException("AI_TIMEOUT", "DeepSeek 连接超时", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ChatException("AI_UNAVAILABLE", "DeepSeek 请求被中断", ex);
        } catch (Exception ex) {
            log.warn("deepseek request failed: {}", ex.getMessage());
            throw new ChatException("AI_UNAVAILABLE", "DeepSeek 请求失败", ex);
        }
    }

    private List<Map<String, String>> toPayload(List<ChatMessage> messages) {
        List<Map<String, String>> payload = new ArrayList<>(messages.size());
        for (ChatMessage message : messages) {
            payload.add(Map.of("role", message.role(), "content", message.content()));
        }
        return payload;
    }

    private int estimateTokens(List<ChatMessage> messages) {
        int total = 0;
        for (ChatMessage message : messages) {
            total += estimateTokens(message.content());
        }
        return total;
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int cjk = 0;
        int latinWords = 0;
        int other = 0;
        boolean inWord = false;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            if (isCjk(codePoint)) {
                cjk++;
                inWord = false;
            } else if (Character.isLetterOrDigit(codePoint)) {
                if (!inWord) {
                    latinWords++;
                    inWord = true;
                }
            } else {
                if (!Character.isWhitespace(codePoint)) {
                    other++;
                }
                inWord = false;
            }
            i += Character.charCount(codePoint);
        }
        int estimate = cjk + (int) Math.ceil(latinWords * 1.3) + (int) Math.ceil(other * 0.3);
        return Math.max(1, estimate);
    }

    private boolean isCjk(int codePoint) {
        return (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
                || (codePoint >= 0x3400 && codePoint <= 0x4DBF)
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF);
    }
}
