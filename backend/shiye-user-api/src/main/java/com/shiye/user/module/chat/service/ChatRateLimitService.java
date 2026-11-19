package com.shiye.user.module.chat.service;

import com.shiye.user.common.settings.RuntimeSystemSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * 问答限流：默认 10 次/分钟/用户，可在系统设置中调整。Redis 不可用时降级放行。
 */
@Service
public class ChatRateLimitService {

    private static final Logger log = LoggerFactory.getLogger(ChatRateLimitService.class);

    private final StringRedisTemplate redisTemplate;
    private final RuntimeSystemSettingsService systemSettingsService;

    public ChatRateLimitService(StringRedisTemplate redisTemplate,
                                RuntimeSystemSettingsService systemSettingsService) {
        this.redisTemplate = redisTemplate;
        this.systemSettingsService = systemSettingsService;
    }

    public RateLimitResult hit(long userId) {
        int limitPerMinute = systemSettingsService.current().chatRateLimitPerMinute();
        long minute = Instant.now().getEpochSecond() / 60;
        long reset = (minute + 1) * 60;
        String key = "shiye:rl:chat:" + userId + ":" + minute;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, Duration.ofMinutes(2));
            long used = count == null ? 0 : count;
            long remaining = Math.max(0, limitPerMinute - used);
            return new RateLimitResult(limitPerMinute, remaining, reset, used > limitPerMinute);
        } catch (RuntimeException ex) {
            log.warn("chat rate limit unavailable, fail open: {}", ex.getMessage());
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
