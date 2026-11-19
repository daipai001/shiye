package com.shiye.user.module.usage.service.impl;

import com.shiye.common.api.ErrorCode;
import com.shiye.common.api.PageResult;
import com.shiye.common.exception.BizException;
import com.shiye.common.settings.SystemSettings;
import com.shiye.common.util.Csv;
import com.shiye.user.common.settings.RuntimeSystemSettingsService;
import com.shiye.user.module.auth.domain.vo.UserQuotaVO;
import com.shiye.user.module.auth.service.AuthService;
import com.shiye.user.module.usage.domain.entity.AiUsageEntity;
import com.shiye.user.module.usage.domain.entity.UsageBreakdownRow;
import com.shiye.user.module.usage.domain.entity.UsageDailyRow;
import com.shiye.user.module.usage.domain.entity.UsageRangeTotalsRow;
import com.shiye.user.module.usage.domain.entity.UserUsageMonthlyEntity;
import com.shiye.user.module.usage.domain.vo.QuotaVO;
import com.shiye.user.module.usage.domain.vo.RecordedUsage;
import com.shiye.user.module.usage.domain.vo.UsageBreakdownVO;
import com.shiye.user.module.usage.domain.vo.UsageDailyVO;
import com.shiye.user.module.usage.domain.vo.UsageRecordVO;
import com.shiye.user.module.usage.domain.vo.UsageSummaryVO;
import com.shiye.user.module.usage.domain.vo.UsageTotals;
import com.shiye.user.module.usage.mapper.AiUsageMapper;
import com.shiye.user.module.usage.mapper.UserUsageMonthlyMapper;
import com.shiye.user.module.usage.service.UsageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UsageServiceImpl implements UsageService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AuthService authService;
    private final AiUsageMapper aiUsageMapper;
    private final UserUsageMonthlyMapper monthlyMapper;
    private final RuntimeSystemSettingsService systemSettingsService;

    public UsageServiceImpl(
            AuthService authService,
            AiUsageMapper aiUsageMapper,
            UserUsageMonthlyMapper monthlyMapper,
            RuntimeSystemSettingsService systemSettingsService) {
        this.authService = authService;
        this.aiUsageMapper = aiUsageMapper;
        this.monthlyMapper = monthlyMapper;
        this.systemSettingsService = systemSettingsService;
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
        SystemSettings settings = systemSettingsService.current();
        QuotaVO quota = quota(userId);
        if (quota.tokenRemaining() <= 0) {
            throw new BizException(ErrorCode.USER_QUOTA_EXCEEDED, "本月 token 配额已用完");
        }
        if (quota.costRemaining().signum() <= 0) {
            throw new BizException(ErrorCode.USER_QUOTA_EXCEEDED, "本月成本配额已用完");
        }
        if (settings.dailyTokenLimit() > 0) {
            LocalDateTime todayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay();
            long todayTokens = aiUsageMapper.sumTokensSince(userId, todayStart);
            if (todayTokens >= settings.dailyTokenLimit()) {
                throw new BizException(ErrorCode.DAILY_TOKEN_LIMIT, "今日 token 已用完");
            }
        }

        UsageTotals totals = monthlyMapper.sumByMonth(currentMonth());
        if (totals != null
                && BigDecimal.valueOf(totals.tokens())
                .compareTo(BigDecimal.valueOf(settings.globalMonthlyTokens())) >= 0) {
            throw new BizException(ErrorCode.GLOBAL_BUDGET_EXCEEDED, "全站 token 预算已用完");
        }
        if (totals != null && totals.cost().compareTo(settings.globalMonthlyCost()) >= 0) {
            throw new BizException(ErrorCode.GLOBAL_BUDGET_EXCEEDED, "全站成本预算已用完");
        }
    }

    @Override
    @Transactional
    public RecordedUsage recordChat(long userId, String requestId, String source, String model,
                                    int promptTokens, int completionTokens,
                                    BigDecimal inputPrice, BigDecimal outputPrice,
                                    int latencyMs, Integer firstTokenMs,
                                    boolean estimated, Long conversationId) {
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
                                 String errorCode, int latencyMs, Long conversationId) {
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
    @Transactional
    public void recordRerank(long userId, String requestId, String source, String model,
                             int tokens, int latencyMs, long conversationId) {
        AiUsageEntity entity = new AiUsageEntity();
        entity.setRequestId(requestId);
        entity.setUserId(userId);
        entity.setSource(source);
        entity.setScene("RERANK");
        entity.setModel(model);
        entity.setPromptTokens(Math.max(0, tokens));
        entity.setCompletionTokens(0);
        entity.setTotalTokens(Math.max(0, tokens));
        entity.setCachedTokens(0);
        entity.setInputPrice(BigDecimal.ZERO);
        entity.setOutputPrice(BigDecimal.ZERO);
        entity.setCost(BigDecimal.ZERO);
        entity.setCurrency("CNY");
        entity.setLatencyMs(latencyMs);
        entity.setStatus("SUCCESS");
        entity.setEstimated(false);
        entity.setConversationId(conversationId);
        aiUsageMapper.insert(entity);
        monthlyMapper.increment(userId, currentMonth(), Math.max(0, tokens), BigDecimal.ZERO);
    }

    @Override
    @Transactional
    public void recordSummary(long userId, String requestId, String source, String model,
                              int promptTokens, int completionTokens,
                              BigDecimal inputPrice, BigDecimal outputPrice,
                              int latencyMs, boolean estimated, long articleId) {
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
        entity.setScene("SUMMARY");
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
        entity.setStatus("SUCCESS");
        entity.setEstimated(estimated);
        entity.setArticleId(articleId);
        aiUsageMapper.insert(entity);
        monthlyMapper.increment(userId, currentMonth(), totalTokens, cost);
    }

    @Override
    @Transactional
    public void recordEmbedding(long userId, String requestId, String source, String model,
                                int tokens, int latencyMs, long articleId) {
        int safeTokens = Math.max(0, tokens);
        AiUsageEntity entity = new AiUsageEntity();
        entity.setRequestId(requestId);
        entity.setUserId(userId);
        entity.setSource(source);
        entity.setScene("EMBEDDING");
        entity.setModel(model);
        entity.setPromptTokens(safeTokens);
        entity.setCompletionTokens(0);
        entity.setTotalTokens(safeTokens);
        entity.setCachedTokens(0);
        entity.setInputPrice(BigDecimal.ZERO);
        entity.setOutputPrice(BigDecimal.ZERO);
        entity.setCost(BigDecimal.ZERO);
        entity.setCurrency("CNY");
        entity.setLatencyMs(latencyMs);
        entity.setStatus("SUCCESS");
        entity.setEstimated(true);
        entity.setArticleId(articleId);
        aiUsageMapper.insert(entity);
        monthlyMapper.increment(userId, currentMonth(), safeTokens, BigDecimal.ZERO);
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

    @Override
    public UsageSummaryVO summary(long userId, int days) {
        int safeDays = Math.max(1, Math.min(days, 30));
        LocalDate to = LocalDate.now(ZoneOffset.UTC);
        LocalDate fromDate = to.minusDays(safeDays - 1L);
        LocalDateTime from = fromDate.atStartOfDay();

        UsageRangeTotalsRow totals = aiUsageMapper.summary(userId, from);
        List<UsageBreakdownVO> scenes = aiUsageMapper.summaryByScene(userId, from).stream()
                .map(row -> new UsageBreakdownVO(
                        row.getLabel(),
                        longValue(row.getCalls()),
                        longValue(row.getTokens()),
                        money(row.getCost())))
                .toList();
        List<UsageBreakdownVO> sources = aiUsageMapper.summaryBySource(userId, from).stream()
                .map(row -> new UsageBreakdownVO(
                        row.getLabel(),
                        longValue(row.getCalls()),
                        longValue(row.getTokens()),
                        money(row.getCost())))
                .toList();

        Map<LocalDate, UsageDailyRow> byDate = new HashMap<>();
        for (UsageDailyRow row : aiUsageMapper.summaryDaily(userId, from)) {
            if (row.getDate() != null) {
                byDate.put(row.getDate(), row);
            }
        }
        List<UsageDailyVO> daily = new ArrayList<>(safeDays);
        for (LocalDate date = fromDate; !date.isAfter(to); date = date.plusDays(1)) {
            UsageDailyRow row = byDate.get(date);
            daily.add(new UsageDailyVO(
                    date,
                    row == null ? 0 : longValue(row.getCalls()),
                    row == null ? 0 : longValue(row.getTokens()),
                    row == null ? BigDecimal.ZERO : money(row.getCost()),
                    row == null ? 0 : longValue(row.getErrors())
            ));
        }

        return new UsageSummaryVO(
                fromDate,
                to,
                safeDays,
                totals == null ? 0 : longValue(totals.getCalls()),
                totals == null ? 0 : longValue(totals.getTokens()),
                totals == null ? BigDecimal.ZERO : money(totals.getCost()),
                totals == null ? 0 : longValue(totals.getErrors()),
                scenes,
                sources,
                daily
        );
    }

    @Override
    public String exportCsv(long userId, String source, String scene, String model, String status) {
        List<AiUsageEntity> rows = aiUsageMapper.findForExport(
                userId, blankToNull(source), blankToNull(scene), blankToNull(model),
                blankToNull(status));
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("createdAt,source,scene,model,promptTokens,completionTokens,totalTokens,"
                + "cost,currency,latencyMs,firstTokenMs,status,errorCode\n");
        for (AiUsageEntity row : rows) {
            csv.append(Csv.escape(row.getCreatedAt()))
                    .append(',').append(Csv.escape(row.getSource()))
                    .append(',').append(Csv.escape(row.getScene()))
                    .append(',').append(Csv.escape(row.getModel()))
                    .append(',').append(row.getPromptTokens() == null ? 0 : row.getPromptTokens())
                    .append(',').append(row.getCompletionTokens() == null ? 0 : row.getCompletionTokens())
                    .append(',').append(row.getTotalTokens() == null ? 0 : row.getTotalTokens())
                    .append(',').append(row.getCost() == null ? BigDecimal.ZERO : row.getCost())
                    .append(',').append(Csv.escape(row.getCurrency()))
                    .append(',').append(row.getLatencyMs() == null ? 0 : row.getLatencyMs())
                    .append(',').append(row.getFirstTokenMs() == null ? "" : row.getFirstTokenMs())
                    .append(',').append(Csv.escape(row.getStatus()))
                    .append(',').append(Csv.escape(row.getErrorCode()))
                    .append('\n');
        }
        return csv.toString();
    }

    private String csvValue(Object value) {
        return Csv.escape(value);
    }

    private long longValue(Long value) {
        return value == null ? 0 : value;
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String currentMonth() {
        return YearMonth.now(ZoneOffset.UTC).toString();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
