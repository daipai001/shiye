package com.shiye.user.common.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiye.common.settings.SystemSettings;
import com.shiye.common.settings.SystemSettingsNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * user-api 运行时系统设置：注册开关、默认配额、全局预算和限流，5 秒缓存。
 */
@Service
public class RuntimeSystemSettingsService {

    private static final Logger log = LoggerFactory.getLogger(RuntimeSystemSettingsService.class);

    private final SystemSettingsMapper mapper;
    private final ObjectMapper objectMapper;
    private final SystemSettings defaults;
    private final long refreshMs;

    private volatile SystemSettings cached;
    private volatile long loadedAt;

    public RuntimeSystemSettingsService(
            SystemSettingsMapper mapper,
            ObjectMapper objectMapper,
            @Value("${shiye.registration.enabled:true}") boolean registrationEnabled,
            @Value("${shiye.registration.default-token-quota:200000}") long defaultMonthlyTokens,
            @Value("${shiye.registration.default-cost-quota:5.000000}") BigDecimal defaultMonthlyCost,
            @Value("${shiye.usage.global-monthly-tokens:2000000}") long globalMonthlyTokens,
            @Value("${shiye.usage.global-monthly-cost:20.00}") BigDecimal globalMonthlyCost,
            @Value("${shiye.usage.alert-threshold:0.8}") double alertThreshold,
            @Value("${shiye.chat.rate-limit-per-minute:10}") int chatRateLimitPerMinute,
            @Value("${shiye.usage.daily-token-limit:0}") long dailyTokenLimit,
            @Value("${shiye.article.chunk-target-size:700}") int ragChunkTargetSize,
            @Value("${shiye.article.chunk-overlap:100}") int ragChunkOverlap,
            @Value("${shiye.ai.retrieval.top-k:8}") int ragTopK,
            @Value("${shiye.ai.retrieval.hybrid-enabled:true}") boolean ragHybridEnabled,
            @Value("${shiye.ai.retrieval.rerank-enabled:true}") boolean ragRerankEnabled,
            @Value("${shiye.ai.citation-required:true}") boolean ragCitationRequired,
            @Value("${shiye.registration.invitation-required:false}") boolean invitationRequired,
            @Value("${shiye.registration.invitation-code:}") String invitationCode,
            @Value("${shiye.settings.refresh-ms:5000}") long refreshMs) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.refreshMs = Math.max(1000, refreshMs);
        this.defaults = new SystemSettings(
                registrationEnabled,
                defaultMonthlyTokens,
                defaultMonthlyCost == null ? BigDecimal.ZERO : defaultMonthlyCost,
                globalMonthlyTokens,
                globalMonthlyCost == null ? BigDecimal.ZERO : globalMonthlyCost,
                alertThreshold,
                chatRateLimitPerMinute,
                dailyTokenLimit,
                ragChunkTargetSize,
                ragChunkOverlap,
                ragTopK,
                ragHybridEnabled,
                ragRerankEnabled,
                ragCitationRequired,
                invitationRequired,
                invitationCode
        );
        this.cached = defaults;
    }

    public SystemSettings current() {
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

    private SystemSettings load() {
        try {
            String json = mapper.findSystemSettings();
            if (json == null || json.isBlank()) {
                return defaults;
            }
            SystemSettings parsed = objectMapper.readValue(json, SystemSettings.class);
            return SystemSettingsNormalizer.normalize(defaults, parsed);
        } catch (Exception ex) {
            log.warn("load runtime system settings failed, fallback to defaults: {}", ex.getMessage());
            return defaults;
        }
    }
}
