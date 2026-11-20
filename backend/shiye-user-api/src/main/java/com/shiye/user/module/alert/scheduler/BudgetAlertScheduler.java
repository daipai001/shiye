package com.shiye.user.module.alert.scheduler;

import com.shiye.common.settings.SystemSettings;
import com.shiye.common.webhook.WebhookClient;
import com.shiye.user.common.settings.RuntimeSystemSettingsService;
import com.shiye.user.module.alert.support.BudgetAlertEvaluator;
import com.shiye.user.module.usage.domain.vo.UsageTotals;
import com.shiye.user.module.usage.service.UsageService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全站月度预算告警：达到阈值发 WARNING，用尽发 EXCEEDED。
 * 同一月份同一级别只成功发送一次；Webhook 失败不写去重键，下个周期重试。
 */
@Component
@ConditionalOnProperty(name = "shiye.alert.budget-scheduler-enabled",
        havingValue = "true", matchIfMissing = true)
public class BudgetAlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(BudgetAlertScheduler.class);
    private static final String LEVEL_WARNING = "WARNING";
    private static final String LEVEL_EXCEEDED = "EXCEEDED";

    private final UsageService usageService;
    private final RuntimeSystemSettingsService settingsService;
    private final WebhookClient webhookClient;
    private final StringRedisTemplate redisTemplate;
    private final Counter webhookFailures;

    public BudgetAlertScheduler(UsageService usageService,
                                RuntimeSystemSettingsService settingsService,
                                WebhookClient webhookClient,
                                StringRedisTemplate redisTemplate,
                                MeterRegistry meterRegistry) {
        this.usageService = usageService;
        this.settingsService = settingsService;
        this.webhookClient = webhookClient;
        this.redisTemplate = redisTemplate;
        this.webhookFailures = Counter.builder("shiye.alert.webhook.failures")
                .description("Budget alert webhook send failures")
                .register(meterRegistry);
    }

    @Scheduled(initialDelayString = "${shiye.alert.budget-initial-delay-ms:30000}",
            fixedDelayString = "${shiye.alert.budget-delay-ms:60000}")
    public void tick() {
        SystemSettings settings = settingsService.current();
        if (!Boolean.TRUE.equals(settings.alertWebhookEnabled())) {
            return;
        }
        String url = settings.alertWebhookUrl();
        if (url == null || url.isBlank()) {
            return;
        }
        UsageTotals totals = usageService.monthTotals();
        BudgetAlertEvaluator.BudgetAlert alert = BudgetAlertEvaluator.evaluate(
                totals.tokens(),
                totals.cost(),
                settings.globalMonthlyTokens(),
                settings.globalMonthlyCost(),
                settings.alertThreshold());
        if (alert == null) {
            return;
        }
        String month = YearMonth.now(ZoneOffset.UTC).toString();
        String dedupeKey = dedupeKey(month, alert.level());
        if (alreadySent(dedupeKey)) {
            return;
        }
        WebhookClient.WebhookResult result = webhookClient.send(url,
                payload(settings, totals, alert, month));
        if (!result.ok()) {
            webhookFailures.increment();
            log.warn("budget alert {} for {} not sent: {}", alert.level(), month,
                    result.message());
            return;
        }
        markSent(dedupeKey);
        if (LEVEL_EXCEEDED.equals(alert.level())) {
            // 已用尽时不再补发 WARNING，避免同月重复打扰
            markSent(dedupeKey(month, LEVEL_WARNING));
        }
        log.info("budget alert {} for {} sent", alert.level(), month);
    }

    private Map<String, Object> payload(SystemSettings settings, UsageTotals totals,
                                        BudgetAlertEvaluator.BudgetAlert alert,
                                        String month) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "BUDGET_ALERT");
        payload.put("level", alert.level());
        payload.put("month", month);
        payload.put("tokens", totals.tokens());
        payload.put("cost", money(totals.cost()));
        payload.put("tokenBudget", settings.globalMonthlyTokens());
        payload.put("costBudget", money(settings.globalMonthlyCost()));
        payload.put("tokenRatio", ratio(alert.tokenRatio()));
        payload.put("costRatio", ratio(alert.costRatio()));
        payload.put("alertThreshold", settings.alertThreshold());
        payload.put("message", LEVEL_EXCEEDED.equals(alert.level())
                ? "全站月度 AI 预算已用尽"
                : "全站月度 AI 预算已达到告警阈值");
        payload.put("sentAt", Instant.now().atOffset(ZoneOffset.UTC).toString());
        return payload;
    }

    private BigDecimal ratio(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal value) {
        return value == null || value.signum() == 0 ? BigDecimal.ZERO : value;
    }

    private String dedupeKey(String month, String level) {
        return "shiye:alert:budget:" + month + ":" + level;
    }

    private boolean alreadySent(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (RuntimeException ex) {
            log.warn("budget alert dedupe unavailable, fail open: {}", ex.getMessage());
            return false;
        }
    }

    private void markSent(String key) {
        try {
            LocalDate firstOfNextMonth = LocalDate.now(ZoneOffset.UTC)
                    .withDayOfMonth(1)
                    .plusMonths(1);
            Duration ttl = Duration.between(Instant.now(),
                    firstOfNextMonth.atStartOfDay().toInstant(ZoneOffset.UTC)).plusDays(7);
            if (ttl.isNegative() || ttl.isZero()) {
                ttl = Duration.ofDays(1);
            }
            redisTemplate.opsForValue().set(key, "1", ttl);
        } catch (RuntimeException ex) {
            log.warn("mark budget alert sent failed: {}", ex.getMessage());
        }
    }
}
