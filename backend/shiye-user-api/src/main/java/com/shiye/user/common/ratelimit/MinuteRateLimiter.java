package com.shiye.user.common.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Redis 分钟窗口限流。Redis 不可用时降级放行，返回 null。
 */
@Service
public class MinuteRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(MinuteRateLimiter.class);

    private final StringRedisTemplate redisTemplate;

    public MinuteRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public RateLimitResult hit(String bucket, String subject, int limitPerMinute) {
        int limit = Math.max(1, limitPerMinute);
        String safeSubject = subject == null || subject.isBlank() ? "unknown" : subject.trim();
        if (safeSubject.length() > 64) {
            safeSubject = safeSubject.substring(0, 64);
        }
        long minute = Instant.now().getEpochSecond() / 60;
        long reset = (minute + 1) * 60;
        String key = "shiye:rl:" + bucket + ":" + safeSubject + ":" + minute;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, Duration.ofMinutes(2));
            long used = count == null ? 0 : count;
            long remaining = Math.max(0, limit - used);
            return new RateLimitResult(limit, remaining, reset, used > limit);
        } catch (RuntimeException ex) {
            log.warn("{} rate limit unavailable, fail open: {}", bucket, ex.getMessage());
            return null;
        }
    }

    public record RateLimitResult(
            int limit,
            long remaining,
            long resetEpochSeconds,
            boolean exceeded
    ) {
    }
}
