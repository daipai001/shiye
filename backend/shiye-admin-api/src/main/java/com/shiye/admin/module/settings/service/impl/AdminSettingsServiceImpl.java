package com.shiye.admin.module.settings.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiye.admin.module.settings.domain.dto.AdminBudgetUpdateRequest;
import com.shiye.admin.module.settings.domain.dto.AdminSettingsUpdateRequest;
import com.shiye.admin.module.settings.domain.vo.AdminSettingsVO;
import com.shiye.admin.module.settings.mapper.AdminSettingsMapper;
import com.shiye.admin.module.settings.service.AdminSettingsService;
import com.shiye.common.settings.SystemSettings;
import com.shiye.common.settings.SystemSettingsNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class AdminSettingsServiceImpl implements AdminSettingsService {

    private static final Logger log = LoggerFactory.getLogger(AdminSettingsServiceImpl.class);

    private final AdminSettingsMapper mapper;
    private final ObjectMapper objectMapper;
    private final SystemSettings defaults;

    public AdminSettingsServiceImpl(
            AdminSettingsMapper mapper,
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
            @Value("${shiye.registration.invitation-code:}") String invitationCode) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
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
    }

    @Override
    public AdminSettingsVO get() {
        return toVO(current(), fromDatabase() ? "DATABASE" : "ENV");
    }

    @Override
    @Transactional
    public AdminSettingsVO update(AdminSettingsUpdateRequest request, long adminId) {
        SystemSettings next = new SystemSettings(
                request.registrationEnabled(),
                request.defaultMonthlyTokens(),
                request.defaultMonthlyCost(),
                request.globalMonthlyTokens(),
                request.globalMonthlyCost(),
                request.alertThreshold(),
                request.chatRateLimitPerMinute(),
                request.dailyTokenLimit(),
                request.ragChunkTargetSize(),
                request.ragChunkOverlap(),
                request.ragTopK(),
                request.ragHybridEnabled(),
                request.ragRerankEnabled(),
                request.ragCitationRequired(),
                request.registrationInvitationRequired(),
                request.registrationInvitationCode()
        );
        SystemSettings saved = save(next, adminId);
        return toVO(saved, "DATABASE");
    }

    @Override
    @Transactional
    public AdminSettingsVO updateBudget(AdminBudgetUpdateRequest request, long adminId) {
        SystemSettings current = current();
        SystemSettings next = new SystemSettings(
                current.registrationEnabled(),
                current.defaultMonthlyTokens(),
                current.defaultMonthlyCost(),
                request.globalMonthlyTokens(),
                request.globalMonthlyCost(),
                request.alertThreshold(),
                current.chatRateLimitPerMinute(),
                current.dailyTokenLimit(),
                current.ragChunkTargetSize(),
                current.ragChunkOverlap(),
                current.ragTopK(),
                current.ragHybridEnabled(),
                current.ragRerankEnabled(),
                current.ragCitationRequired(),
                current.registrationInvitationRequired(),
                current.registrationInvitationCode()
        );
        SystemSettings saved = save(next, adminId);
        return toVO(saved, "DATABASE");
    }

    @Override
    public SystemSettings current() {
        try {
            String json = mapper.findSettings();
            if (json == null || json.isBlank()) {
                return defaults;
            }
            SystemSettings parsed = objectMapper.readValue(json, SystemSettings.class);
            return SystemSettingsNormalizer.normalize(defaults, parsed);
        } catch (Exception ex) {
            log.warn("load system settings failed, fallback to defaults: {}", ex.getMessage());
            return defaults;
        }
    }

    private SystemSettings save(SystemSettings settings, long adminId) {
        SystemSettings normalized = SystemSettingsNormalizer.normalize(defaults, settings);
        try {
            mapper.upsertSettings(objectMapper.writeValueAsString(normalized), adminId);
            return normalized;
        } catch (Exception ex) {
            throw new IllegalStateException("保存系统设置失败", ex);
        }
    }

    private boolean fromDatabase() {
        try {
            String json = mapper.findSettings();
            return json != null && !json.isBlank();
        } catch (RuntimeException ex) {
            log.warn("check system settings source failed: {}", ex.getMessage());
            return false;
        }
    }

    private AdminSettingsVO toVO(SystemSettings settings, String source) {
        return new AdminSettingsVO(
                settings.registrationEnabled(),
                settings.defaultMonthlyTokens(),
                settings.defaultMonthlyCost(),
                settings.globalMonthlyTokens(),
                settings.globalMonthlyCost(),
                settings.alertThreshold(),
                settings.chatRateLimitPerMinute(),
                settings.dailyTokenLimit(),
                settings.ragChunkTargetSize(),
                settings.ragChunkOverlap(),
                settings.ragTopK(),
                Boolean.TRUE.equals(settings.ragHybridEnabled()),
                Boolean.TRUE.equals(settings.ragRerankEnabled()),
                Boolean.TRUE.equals(settings.ragCitationRequired()),
                Boolean.TRUE.equals(settings.registrationInvitationRequired()),
                settings.registrationInvitationCode(),
                source
        );
    }
}
