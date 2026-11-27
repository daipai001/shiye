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
import com.shiye.user.module.usage.domain.entity.TeamUsageMonthlyEntity;
import com.shiye.user.module.usage.domain.entity.UsageRangeTotalsRow;
import com.shiye.user.module.usage.domain.entity.UsageReservationEntity;
import com.shiye.user.module.usage.domain.entity.UserUsageMonthlyEntity;
import com.shiye.user.module.usage.domain.vo.ChatReservation;
import com.shiye.user.module.usage.domain.vo.GlobalReservation;
import com.shiye.user.module.usage.domain.vo.QuotaVO;
import com.shiye.user.module.usage.domain.vo.RecordedUsage;
import com.shiye.user.module.usage.domain.vo.TeamReservation;
import com.shiye.user.module.usage.domain.vo.UserReservation;
import com.shiye.user.module.usage.domain.vo.UsageBreakdownVO;
import com.shiye.user.module.usage.domain.vo.UsageDailyVO;
import com.shiye.user.module.usage.domain.vo.UsageRecordVO;
import com.shiye.user.module.usage.domain.vo.UsageSummaryVO;
import com.shiye.user.module.usage.domain.vo.UsageTotals;
import com.shiye.user.module.usage.mapper.AiUsageMapper;
import com.shiye.user.module.usage.mapper.GlobalUsageMonthlyMapper;
import com.shiye.user.module.usage.mapper.TeamUsageMonthlyMapper;
import com.shiye.user.module.usage.mapper.UsageReservationMapper;
import com.shiye.user.module.usage.mapper.UserUsageMonthlyMapper;
import com.shiye.user.module.usage.service.UsageService;
import com.shiye.user.module.team.domain.vo.TeamQuota;
import com.shiye.user.module.team.service.TeamService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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

    private static final Logger log = LoggerFactory.getLogger(UsageServiceImpl.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final AuthService authService;
    private final AiUsageMapper aiUsageMapper;
    private final UserUsageMonthlyMapper monthlyMapper;
    private final TeamUsageMonthlyMapper teamUsageMapper;
    private final GlobalUsageMonthlyMapper globalUsageMapper;
    private final UsageReservationMapper reservationMapper;
    private final TransactionTemplate transactionTemplate;
    private final TeamService teamService;
    private final RuntimeSystemSettingsService systemSettingsService;
    private final Counter chatSuccess;
    private final Counter chatFailed;
    private final long chatReserveTokens;
    private final BigDecimal chatReserveCost;
    private final long reservationTtlMinutes;

    public UsageServiceImpl(
            AuthService authService,
            AiUsageMapper aiUsageMapper,
            UserUsageMonthlyMapper monthlyMapper,
            TeamUsageMonthlyMapper teamUsageMapper,
            GlobalUsageMonthlyMapper globalUsageMapper,
            UsageReservationMapper reservationMapper,
            TransactionTemplate transactionTemplate,
            TeamService teamService,
            RuntimeSystemSettingsService systemSettingsService,
            MeterRegistry meterRegistry,
            @Value("${shiye.usage.chat-reserve-tokens:8000}") long chatReserveTokens,
            @Value("${shiye.usage.chat-reserve-cost:0.06}") BigDecimal chatReserveCost,
            @Value("${shiye.usage.reservation-ttl-minutes:15}") long reservationTtlMinutes) {
        this.authService = authService;
        this.aiUsageMapper = aiUsageMapper;
        this.monthlyMapper = monthlyMapper;
        this.teamUsageMapper = teamUsageMapper;
        this.globalUsageMapper = globalUsageMapper;
        this.reservationMapper = reservationMapper;
        this.transactionTemplate = transactionTemplate;
        this.teamService = teamService;
        this.systemSettingsService = systemSettingsService;
        this.chatSuccess = Counter.builder("shiye.ai.calls")
                .tag("scene", "CHAT").tag("status", "SUCCESS").register(meterRegistry);
        this.chatFailed = Counter.builder("shiye.ai.calls")
                .tag("scene", "CHAT").tag("status", "FAILED").register(meterRegistry);
        this.chatReserveTokens = Math.max(1000, chatReserveTokens);
        this.chatReserveCost = chatReserveCost == null || chatReserveCost.signum() < 0
                ? BigDecimal.ZERO : chatReserveCost;
        this.reservationTtlMinutes = Math.max(5, reservationTtlMinutes);
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
    public UsageTotals monthTotals() {
        UsageTotals totals = monthlyMapper.sumByMonth(currentMonth());
        return totals == null ? new UsageTotals(0, BigDecimal.ZERO, 0) : totals;
    }

    @Override
    public void assertCanChat(long userId) {
        assertCanChat(userId, null);
    }

    @Override
    public void assertCanChat(long userId, Long teamId) {
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
        if (teamId != null) {
            teamService.requireMember(userId, teamId);
            TeamQuota teamQuota = teamService.quota(teamId);
            TeamUsageMonthlyEntity teamUsage = teamUsageMapper.find(teamId, currentMonth());
            long teamTokens = teamUsage == null || teamUsage.getTokens() == null
                    ? 0 : teamUsage.getTokens();
            BigDecimal teamCost = teamUsage == null || teamUsage.getCost() == null
                    ? BigDecimal.ZERO : teamUsage.getCost();
            if (teamTokens >= teamQuota.tokenQuota()) {
                throw new BizException(ErrorCode.TEAM_QUOTA_EXCEEDED, "团队本月 token 配额已用完");
            }
            if (teamCost.compareTo(teamQuota.costQuota()) >= 0) {
                throw new BizException(ErrorCode.TEAM_QUOTA_EXCEEDED, "团队本月成本配额已用完");
            }
        }

        UsageTotals totals = globalTotalsWithFallback();
        if (totals != null
                && BigDecimal.valueOf(totals.tokens())
                .compareTo(BigDecimal.valueOf(settings.globalMonthlyTokens())) >= 0) {
            throw new BizException(ErrorCode.GLOBAL_BUDGET_EXCEEDED, "全站 token 预算已用完");
        }
        if (totals != null && totals.cost() != null
                && totals.cost().compareTo(settings.globalMonthlyCost()) >= 0) {
            throw new BizException(ErrorCode.GLOBAL_BUDGET_EXCEEDED, "全站成本预算已用完");
        }
    }

    @Override
    @Transactional
    public TeamReservation reserveTeamChat(long userId, long teamId, long tokens,
                                           BigDecimal cost) {
        teamService.requireMember(userId, teamId);
        TeamQuota quota = teamService.quota(teamId);
        String month = currentMonth();
        // 同一团队的月度行初始化/预留串行化，避免并发 INSERT 空行造成死锁。
        if (teamUsageMapper.lockTeamRow(teamId) == null) {
            throw new BizException(ErrorCode.TEAM_QUOTA_EXCEEDED, "团队不存在或已解散");
        }
        teamUsageMapper.ensureRow(teamId, month);
        int reserved = teamUsageMapper.reserve(
                teamId, month, tokens, cost, quota.tokenQuota(), quota.costQuota());
        if (reserved == 0) {
            throw new BizException(ErrorCode.TEAM_QUOTA_EXCEEDED, "团队本月配额已用完");
        }
        return new TeamReservation(teamId, month, tokens, cost);
    }

    @Override
    public void releaseTeamChat(TeamReservation reservation) {
        if (reservation == null) {
            return;
        }
        try {
            teamUsageMapper.release(
                    reservation.teamId(), reservation.month(),
                    reservation.tokens(), reservation.cost());
        } catch (RuntimeException ex) {
            log.warn("release team quota reservation failed: {}", ex.getMessage());
        }
    }

    @Override
    @Transactional
    public ChatReservation reserveChat(long userId, Long teamId) {
        UserQuotaVO quota = authService.quota(userId);
        String month = currentMonth();
        // 同一用户的配额初始化串行化，避免并发 INSERT 空月度行造成 InnoDB 死锁。
        if (monthlyMapper.lockUserRow(userId) == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        UserUsageMonthlyEntity usage = monthlyMapper.findForUpdate(userId, month);
        if (usage == null) {
            monthlyMapper.insertEmpty(userId, month);
            usage = monthlyMapper.findForUpdate(userId, month);
        }
        long usedTokens = usage == null || usage.getTokens() == null ? 0 : usage.getTokens();
        BigDecimal usedCost = usage == null || usage.getCost() == null
                ? BigDecimal.ZERO : usage.getCost();
        if (usedTokens + chatReserveTokens > quota.monthlyTokenQuota()
                || usedCost.add(chatReserveCost).compareTo(quota.monthlyCostQuota()) > 0) {
            throw new BizException(ErrorCode.USER_QUOTA_EXCEEDED, "本月配额不足以开始本次问答");
        }
        if (monthlyMapper.add(userId, month, chatReserveTokens, chatReserveCost) == 0) {
            throw new BizException(ErrorCode.USER_QUOTA_EXCEEDED, "本月配额预留失败，请稍后重试");
        }
        UserReservation userReservation = new UserReservation(
                userId, month, chatReserveTokens, chatReserveCost);
        // 锁顺序与 recordChat 保持一致：user -> team -> global，避免死锁。
        TeamReservation teamReservation = teamId == null
                ? null
                : reserveTeamChat(userId, teamId, 12_000, new BigDecimal("0.12"));
        GlobalReservation globalReservation = reserveGlobalChat(
                month, chatReserveTokens, chatReserveCost);
        Long reservationId = insertLedger(userId, teamId, month,
                userReservation, teamReservation, globalReservation);
        return new ChatReservation(
                reservationId, userReservation, teamReservation, globalReservation);
    }

    /**
     * 写入预留流水。迁移未执行时只记录错误并继续，保持旧版可用性；
     * 此时释放走兼容路径，崩溃残留只能等月末。
     */
    private Long insertLedger(long userId, Long teamId, String month,
                              UserReservation user,
                              TeamReservation team,
                              GlobalReservation global) {
        UsageReservationEntity entity = new UsageReservationEntity();
        entity.setUserId(userId);
        entity.setTeamId(teamId);
        entity.setMonth(month);
        entity.setUserTokens(user == null ? 0 : user.tokens());
        entity.setUserCost(user == null ? BigDecimal.ZERO : user.cost());
        entity.setTeamTokens(team == null ? 0 : team.tokens());
        entity.setTeamCost(team == null ? BigDecimal.ZERO : team.cost());
        entity.setGlobalTokens(global == null ? 0 : global.tokens());
        entity.setGlobalCost(global == null ? BigDecimal.ZERO : global.cost());
        entity.setStatus("ACTIVE");
        entity.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC)
                .plusMinutes(reservationTtlMinutes));
        try {
            reservationMapper.insert(entity);
            return entity.getId();
        } catch (BadSqlGrammarException ex) {
            log.error("usage_reservations table missing, run migration "
                    + "2026-11-27-usage-reservations.sql: {}", ex.getMessage());
            return null;
        }
    }

    private GlobalReservation reserveGlobalChat(String month, long tokens, BigDecimal cost) {
        try {
            SystemSettings settings = systemSettingsService.current();
            ensureGlobalRow(month);
            int reserved = globalUsageMapper.reserve(
                    month, tokens, cost,
                    settings.globalMonthlyTokens(), settings.globalMonthlyCost());
            if (reserved == 0) {
                throw new BizException(ErrorCode.GLOBAL_BUDGET_EXCEEDED,
                        "全站预算不足以开始本次问答");
            }
            return new GlobalReservation(month, tokens, cost);
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            // 迁移未执行/表暂不可用时回退到旧的全站总量检查，避免问答整体不可用。
            log.error("global budget reservation unavailable, fallback: {}", ex.getMessage());
            return null;
        }
    }

    private void ensureGlobalRow(String month) {
        if (globalUsageMapper.find(month) != null) {
            return;
        }
        String lock = "shiye:global_usage:" + month;
        Integer acquired = globalUsageMapper.acquireLock(lock);
        try {
            // 首次建行时从用户月用量回填，避免升级后全站预算从 0 重新开始。
            globalUsageMapper.initRow(month);
        } finally {
            if (acquired != null && acquired == 1) {
                globalUsageMapper.releaseLock(lock);
            }
        }
    }

    private UsageTotals globalTotalsWithFallback() {
        try {
            UsageTotals totals = globalUsageMapper.find(currentMonth());
            if (totals != null) {
                return totals;
            }
            // 月度行还未初始化时，用用户月用量之和兜底，避免预算从 0 重新开始。
            return monthlyMapper.sumByMonth(currentMonth());
        } catch (RuntimeException ex) {
            log.warn("global usage table unavailable, fallback to user totals: {}", ex.getMessage());
            return monthlyMapper.sumByMonth(currentMonth());
        }
    }

    private void recordGlobalUsage(long tokens, BigDecimal cost, long calls) {
        try {
            globalUsageMapper.add(currentMonth(), tokens, cost, calls);
        } catch (RuntimeException ex) {
            log.error("record global usage failed: {}", ex.getMessage());
        }
    }

    @Override
    public void releaseChat(ChatReservation reservation) {
        if (reservation == null) {
            return;
        }
        Long reservationId = reservation.id();
        try {
            if (reservationId == null) {
                // 流水表不可用时的兼容释放，无法保证崩溃恢复。
                transactionTemplate.executeWithoutResult(
                        status -> releaseAggregates(reservation));
                return;
            }
            Boolean released = transactionTemplate.execute(status -> {
                if (reservationMapper.markReleased(reservationId) == 0) {
                    return false;
                }
                releaseAggregates(reservation);
                return true;
            });
            if (Boolean.FALSE.equals(released)) {
                log.debug("reservation {} already released or reaped", reservationId);
            }
        } catch (RuntimeException ex) {
            // 标记和聚合释放同一事务，失败时留待过期回收任务重试。
            log.error("release reservation {} failed, reaper will retry: {}",
                    reservationId, ex.getMessage());
        }
    }

    /**
     * 回收进程崩溃或异常路径遗留的过期预留。
     * 只有成功把流水从 ACTIVE 改为 RELEASED 的实例才会扣减聚合计数，避免重复释放。
     */
    @Scheduled(fixedDelayString = "${shiye.usage.reservation-reap-delay-ms:60000}",
            initialDelayString = "${shiye.usage.reservation-reap-initial-delay-ms:30000}")
    public void reapExpiredReservations() {
        List<UsageReservationEntity> expired;
        try {
            expired = reservationMapper.findExpired(200);
        } catch (RuntimeException ex) {
            log.warn("load expired usage reservations failed: {}", ex.getMessage());
            return;
        }
        for (UsageReservationEntity entity : expired) {
            try {
                Boolean released = transactionTemplate.execute(status -> {
                    if (reservationMapper.markReleased(entity.getId()) == 0) {
                        return false;
                    }
                    releaseAggregates(entity);
                    return true;
                });
                if (Boolean.TRUE.equals(released)) {
                    log.warn("released expired usage reservation {} for user {} month {}",
                            entity.getId(), entity.getUserId(), entity.getMonth());
                }
            } catch (RuntimeException ex) {
                log.error("release expired reservation {} failed: {}",
                        entity.getId(), ex.getMessage());
            }
        }
    }

    private void releaseAggregates(ChatReservation reservation) {
        // 释放也保持 user -> team -> global 顺序，降低与 record/reserve 的死锁概率。
        UserReservation user = reservation.user();
        if (user != null) {
            monthlyMapper.release(user.userId(), user.month(), user.tokens(), user.cost());
        }
        TeamReservation team = reservation.team();
        if (team != null) {
            teamUsageMapper.release(team.teamId(), team.month(), team.tokens(), team.cost());
        }
        GlobalReservation global = reservation.global();
        if (global != null) {
            globalUsageMapper.release(global.month(), global.tokens(), global.cost());
        }
    }

    private void releaseAggregates(UsageReservationEntity reservation) {
        boolean hasUser = (reservation.getUserTokens() != null
                && reservation.getUserTokens() > 0)
                || (reservation.getUserCost() != null
                && reservation.getUserCost().signum() > 0);
        if (hasUser) {
            monthlyMapper.release(reservation.getUserId(), reservation.getMonth(),
                    longValue(reservation.getUserTokens()), money(reservation.getUserCost()));
        }
        boolean hasTeam = reservation.getTeamId() != null
                && ((reservation.getTeamTokens() != null && reservation.getTeamTokens() > 0)
                || (reservation.getTeamCost() != null
                && reservation.getTeamCost().signum() > 0));
        if (hasTeam) {
            teamUsageMapper.release(reservation.getTeamId(), reservation.getMonth(),
                    longValue(reservation.getTeamTokens()), money(reservation.getTeamCost()));
        }
        boolean hasGlobal = (reservation.getGlobalTokens() != null
                && reservation.getGlobalTokens() > 0)
                || (reservation.getGlobalCost() != null
                && reservation.getGlobalCost().signum() > 0);
        if (hasGlobal) {
            globalUsageMapper.release(reservation.getMonth(),
                    longValue(reservation.getGlobalTokens()),
                    money(reservation.getGlobalCost()));
        }
    }

    @Override
    @Transactional
    public RecordedUsage recordChat(long userId, String requestId, String source, String model,
                                    int promptTokens, int completionTokens,
                                    BigDecimal inputPrice, BigDecimal outputPrice,
                                    int latencyMs, Integer firstTokenMs,
                                    boolean estimated, Long conversationId, Long teamId) {
        int totalTokens = promptTokens + completionTokens;
        BigDecimal safeInput = inputPrice == null ? BigDecimal.ZERO : inputPrice;
        BigDecimal safeOutput = outputPrice == null ? BigDecimal.ZERO : outputPrice;
        BigDecimal cost = cost(promptTokens, completionTokens, safeInput, safeOutput);

        AiUsageEntity entity = new AiUsageEntity();
        entity.setRequestId(requestId);
        entity.setUserId(userId);
        entity.setTeamId(teamId);
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
        if (teamId != null) {
            teamUsageMapper.increment(teamId, currentMonth(), totalTokens, cost);
        }
        recordGlobalUsage(totalTokens, cost, 1);
        chatSuccess.increment();
        return new RecordedUsage(promptTokens, completionTokens, totalTokens, cost, "CNY", estimated);
    }

    @Override
    @Transactional
    public RecordedUsage recordEval(long userId, String requestId, String model,
                                    int promptTokens, int completionTokens,
                                    BigDecimal inputPrice, BigDecimal outputPrice,
                                    int latencyMs, boolean estimated) {
        int totalTokens = promptTokens + completionTokens;
        BigDecimal safeInput = inputPrice == null ? BigDecimal.ZERO : inputPrice;
        BigDecimal safeOutput = outputPrice == null ? BigDecimal.ZERO : outputPrice;
        BigDecimal cost = cost(promptTokens, completionTokens, safeInput, safeOutput);

        AiUsageEntity entity = new AiUsageEntity();
        entity.setRequestId(requestId);
        entity.setUserId(userId);
        entity.setSource("ADMIN");
        entity.setScene("EVAL");
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
        aiUsageMapper.insert(entity);

        monthlyMapper.increment(userId, currentMonth(), totalTokens, cost);
        recordGlobalUsage(totalTokens, cost, 1);
        return new RecordedUsage(promptTokens, completionTokens, totalTokens, cost, "CNY", estimated);
    }

    private BigDecimal cost(int promptTokens, int completionTokens,
                            BigDecimal inputPrice, BigDecimal outputPrice) {
        return BigDecimal.valueOf(promptTokens)
                .multiply(inputPrice)
                .divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.HALF_UP)
                .add(BigDecimal.valueOf(completionTokens)
                        .multiply(outputPrice)
                        .divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.HALF_UP));
    }

    @Override
    @Transactional
    public void recordFailedChat(long userId, String requestId, String source, String model,
                                 String errorCode, int latencyMs, Long conversationId, Long teamId) {
        AiUsageEntity entity = new AiUsageEntity();
        entity.setRequestId(requestId);
        entity.setUserId(userId);
        entity.setTeamId(teamId);
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
        if (teamId != null) {
            teamUsageMapper.increment(teamId, currentMonth(), 0, BigDecimal.ZERO);
        }
        recordGlobalUsage(0, BigDecimal.ZERO, 1);
        chatFailed.increment();
    }

    @Override
    @Transactional
    public void recordRerank(long userId, String requestId, String source, String model,
                             int tokens, int latencyMs, long conversationId, Long teamId) {
        AiUsageEntity entity = new AiUsageEntity();
        entity.setRequestId(requestId);
        entity.setUserId(userId);
        entity.setTeamId(teamId);
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
        if (teamId != null) {
            teamUsageMapper.increment(teamId, currentMonth(), Math.max(0, tokens), BigDecimal.ZERO);
        }
        recordGlobalUsage(Math.max(0, tokens), BigDecimal.ZERO, 1);
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
        BigDecimal cost = cost(promptTokens, completionTokens, safeInput, safeOutput);

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
        recordGlobalUsage(totalTokens, cost, 1);
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
        recordGlobalUsage(safeTokens, BigDecimal.ZERO, 1);
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
