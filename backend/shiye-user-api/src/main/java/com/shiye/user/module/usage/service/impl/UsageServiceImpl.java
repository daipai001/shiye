package com.shiye.user.module.usage.service.impl;

import com.shiye.common.api.ErrorCode;
import com.shiye.common.api.PageResult;
import com.shiye.common.exception.BizException;
import com.shiye.user.module.auth.domain.vo.UserQuotaVO;
import com.shiye.user.module.auth.service.AuthService;
import com.shiye.user.module.usage.domain.entity.AiUsageEntity;
import com.shiye.user.module.usage.domain.entity.UserUsageMonthlyEntity;
import com.shiye.user.module.usage.domain.vo.QuotaVO;
import com.shiye.user.module.usage.domain.vo.RecordedUsage;
import com.shiye.user.module.usage.domain.vo.UsageRecordVO;
import com.shiye.user.module.usage.domain.vo.UsageTotals;
import com.shiye.user.module.usage.mapper.AiUsageMapper;
import com.shiye.user.module.usage.mapper.UserUsageMonthlyMapper;
import com.shiye.user.module.usage.service.UsageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class UsageServiceImpl implements UsageService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AuthService authService;
    private final AiUsageMapper aiUsageMapper;
    private final UserUsageMonthlyMapper monthlyMapper;
    private final BigDecimal globalMonthlyTokens;
    private final BigDecimal globalMonthlyCost;

    public UsageServiceImpl(
            AuthService authService,
            AiUsageMapper aiUsageMapper,
            UserUsageMonthlyMapper monthlyMapper,
            @Value("${shiye.usage.global-monthly-tokens:2000000}") long globalMonthlyTokens,
            @Value("${shiye.usage.global-monthly-cost:20.00}") BigDecimal globalMonthlyCost) {
        this.authService = authService;
        this.aiUsageMapper = aiUsageMapper;
        this.monthlyMapper = monthlyMapper;
        this.globalMonthlyTokens = BigDecimal.valueOf(globalMonthlyTokens);
        this.globalMonthlyCost = globalMonthlyCost;
    }

    @Override
    public QuotaVO quota(long userId) {
        UserQuotaVO userQuota = authService.quota(userId);
        String month = currentMonth();
        UserUsageMonthlyEntity usage = monthlyMapper.find(userId, month);
        long tokensUsed = usage == null || usage.getTokens() == null ? 0 : usage.getTokens();
        BigDecimal costUsed = usage == null || usage.getCost() == null
                ? BigDecimal.ZERO : usage.getCost();
        long tokenRemaining = Math.max(0, userQuota.monthlyTokenQuota() - tokensUsed);
        BigDecimal costRemaining = userQuota.monthlyCostQuota().subtract(costUsed);
        if (costRemaining.signum() < 0) {
            costRemaining = BigDecimal.ZERO;
        }
        LocalDateTime resetAt = YearMonth.from(LocalDate.now(ZoneOffset.UTC))
                .plusMonths(1)
                .atDay(1)
                .atStartOfDay();
        return new QuotaVO(
                month,
                userQuota.monthlyTokenQuota(),
                tokensUsed,
                tokenRemaining,
                userQuota.monthlyCostQuota(),
                costUsed,
                costRemaining,
                resetAt
        );
    }

    @Override
    public void assertCanChat(long userId) {
        QuotaVO quota = quota(userId);
        if (quota.tokenRemaining() <= 0) {
            throw new BizException(ErrorCode.USER_QUOTA_EXCEEDED, "本月 token 配额已用完");
        }
        if (quota.costRemaining().signum() <= 0) {
            throw new BizException(ErrorCode.USER_QUOTA_EXCEEDED, "本月成本配额已用完");
        }

        UsageTotals totals = monthlyMapper.sumByMonth(currentMonth());
        if (totals != null
                && BigDecimal.valueOf(totals.tokens()).compareTo(globalMonthlyTokens) >= 0) {
            throw new BizException(ErrorCode.GLOBAL_BUDGET_EXCEEDED, "全站 token 预算已用完");
        }
        if (totals != null && totals.cost().compareTo(globalMonthlyCost) >= 0) {
            throw new BizException(ErrorCode.GLOBAL_BUDGET_EXCEEDED, "全站成本预算已用完");
        }
    }

    @Override
    @Transactional
    public RecordedUsage recordChat(long userId, String requestId, String source, String model,
                                    int promptTokens, int completionTokens,
                                    BigDecimal inputPrice, BigDecimal outputPrice,
                                    int latencyMs, Integer firstTokenMs,
                                    boolean estimated, long conversationId) {
        int totalTokens = promptTokens + completionTokens;
        BigDecimal safeInput = inputPrice == null ? BigDecimal.ZERO : inputPrice;
        BigDecimal safeOutput = outputPrice == null ? BigDecimal.ZERO : outputPrice;
        BigDecimal cost = BigDecimal.valueOf(promptTokens)
                .multiply(safeInput)
                .divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.HALF_UP)
                .add(BigDecimal.valueOf(completionTokens)
                        .multiply(safeOutput)
                        .divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.HALF_UP));

        AiUsageEntity entity = new AiUsageEntity();
        entity.setRequestId(requestId);
        entity.setUserId(userId);
        entity.setSource(source);
        entity.setScene("CHAT");
        entity.setModel(model);
        entity.setPromptTokens(promptTokens);
        entity.setCompletionTokens(completionTokens);
        entity.setTotalTokens(totalTokens);
        entity.setCachedTokens(0);
        entity.setInputPrice(safeInput);
        entity.setOutputPrice(safeOutput);
        entity.setCost(cost);
        entity.setCurrency("CNY");
        entity.setLatencyMs(latencyMs);
        entity.setFirstTokenMs(firstTokenMs);
        entity.setStatus("SUCCESS");
        entity.setEstimated(estimated);
        entity.setConversationId(conversationId);
        aiUsageMapper.insert(entity);

        monthlyMapper.increment(userId, currentMonth(), totalTokens, cost);
        return new RecordedUsage(promptTokens, completionTokens, totalTokens, cost, "CNY", estimated);
    }

    @Override
    @Transactional
    public void recordFailedChat(long userId, String requestId, String source, String model,
                                 String errorCode, int latencyMs, long conversationId) {
        AiUsageEntity entity = new AiUsageEntity();
        entity.setRequestId(requestId);
        entity.setUserId(userId);
        entity.setSource(source);
        entity.setScene("CHAT");
        entity.setModel(model);
        entity.setPromptTokens(0);
        entity.setCompletionTokens(0);
        entity.setTotalTokens(0);
        entity.setCachedTokens(0);
        entity.setInputPrice(BigDecimal.ZERO);
        entity.setOutputPrice(BigDecimal.ZERO);
        entity.setCost(BigDecimal.ZERO);
        entity.setCurrency("CNY");
        entity.setLatencyMs(latencyMs);
        entity.setStatus("FAILED");
        entity.setErrorCode(errorCode);
        entity.setEstimated(false);
        entity.setConversationId(conversationId);
        aiUsageMapper.insert(entity);
        monthlyMapper.increment(userId, currentMonth(), 0, BigDecimal.ZERO);
    }

    @Override
    public PageResult<UsageRecordVO> records(long userId, int page, int pageSize,
                                             String source, String scene, String model, String status) {
        int safePage = Math.max(1, page);
        int safeSize = pageSize <= 0 ? 20 : Math.min(pageSize, MAX_PAGE_SIZE);
        String sourceFilter = blankToNull(source);
        String sceneFilter = blankToNull(scene);
        String modelFilter = blankToNull(model);
        String statusFilter = blankToNull(status);
        long total = aiUsageMapper.count(userId, sourceFilter, sceneFilter, modelFilter, statusFilter);
        if (total == 0) {
            return PageResult.empty(safePage, safeSize);
        }
        List<UsageRecordVO> items = aiUsageMapper
                .findPage(userId, sourceFilter, sceneFilter, modelFilter, statusFilter,
                        (safePage - 1) * safeSize, safeSize)
                .stream()
                .map(UsageRecordVO::from)
                .toList();
        return PageResult.of(items, total, safePage, safeSize);
    }

    private String currentMonth() {
        return YearMonth.now(ZoneOffset.UTC).toString();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
