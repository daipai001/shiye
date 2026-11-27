package com.shiye.user.module.article.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 单篇文章入库锁：同一资料同时只允许一个抓取/切片/向量化任务。
 * Redis 不可用时 fail-open，保持原有可用性。
 */
@Service
public class ArticleIngestLockService {

    private static final Logger log = LoggerFactory.getLogger(ArticleIngestLockService.class);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final int lockMinutes;
    private final Counter acquired;
    private final Counter busy;
    private final Counter failOpen;
    private final Counter releaseFailed;

    public ArticleIngestLockService(
            StringRedisTemplate redisTemplate,
            @Value("${shiye.article.ingest-lock-minutes:30}") int lockMinutes,
            MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.lockMinutes = Math.max(1, lockMinutes);
        this.acquired = lockCounter(meterRegistry, "acquired");
        this.busy = lockCounter(meterRegistry, "busy");
        this.failOpen = lockCounter(meterRegistry, "fail_open");
        this.releaseFailed = lockCounter(meterRegistry, "release_failed");
    }

    /**
     * @return 锁 token；返回 null 表示已有任务在跑；返回空串表示 Redis 不可用、已 fail-open。
     */
    public String tryAcquire(long articleId) {
        String key = key(articleId);
        String token = UUID.randomUUID().toString();
        try {
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                    key, token, Duration.ofMinutes(lockMinutes));
            if (Boolean.TRUE.equals(locked)) {
                acquired.increment();
                return token;
            }
            busy.increment();
            return null;
        } catch (RuntimeException ex) {
            failOpen.increment();
            log.warn("ingest lock unavailable, fail open for article {}: {}",
                    articleId, ex.getMessage());
            return "";
        }
    }

    public void release(long articleId, String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        try {
            redisTemplate.execute(RELEASE_SCRIPT, List.of(key(articleId)), token);
        } catch (RuntimeException ex) {
            releaseFailed.increment();
            log.warn("release ingest lock failed for article {}: {}", articleId, ex.getMessage());
        }
    }

    private Counter lockCounter(MeterRegistry meterRegistry, String result) {
        return Counter.builder("shiye.ingest.lock")
                .tag("result", result)
                .register(meterRegistry);
    }

    private String key(long articleId) {
        return "shiye:ingest:lock:" + articleId;
    }
}
