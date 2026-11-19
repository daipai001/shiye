package com.shiye.admin.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * 管理端操作限流：默认 60 次/分钟/管理员。Redis 不可用时降级放行。
 */
@Service
public class AdminRateLimitService {

    private static final Logger log = LoggerFactory.getLogger(AdminRateLimitService.class);

    private final StringRedisTemplate redisTemplate;
    private final int limitPerMinute;

    public AdminRateLimitService(StringRedisTemplate redisTemplate,
                                 @Value("${shiye.admin.rate-limit-per-minute:60}") int limitPerMinute) {
        this.redisTemplate = redisTemplate;
        this.limitPerMinute = Math.max(1, limitPerMinute);
    }

    public RateLimitResult hit(long adminId) {
        long minute = Instant.now().getEpochSecond() / 60;
        long reset = (minute + 1) * 60;
        String key = "shiye:rl:admin:" + adminId + ":" + minute;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, Duration.ofMinutes(2));
            long used = count == null ? 0 : count;
            long remaining = Math.max(0, limitPerMinute - used);
            return new RateLimitResult(limitPerMinute, remaining, reset, used > limitPerMinute);
        } catch (RuntimeException ex) {
            log.warn("admin rate limit unavailable, fail open: {}", ex.getMessage());
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
