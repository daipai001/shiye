package com.shiye.user.module.bookmark.service;

import com.shiye.common.api.ErrorCode;
import com.shiye.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * 书签导入创建限流：5 次/分钟/用户。Redis 不可用时降级放行，避免阻断主流程。
 */
@Service
public class BookmarkRateLimitService {

    private static final Logger log = LoggerFactory.getLogger(BookmarkRateLimitService.class);
    private static final int LIMIT_PER_MINUTE = 5;

    private final StringRedisTemplate redisTemplate;

    public BookmarkRateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void checkCreate(long userId) {
        long minute = Instant.now().getEpochSecond() / 60;
        String key = "shiye:rl:bookmark-import:" + userId + ":" + minute;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, Duration.ofMinutes(2));
            if (count != null && count > LIMIT_PER_MINUTE) {
                throw new BizException(ErrorCode.RATE_LIMITED, "书签导入过于频繁，请稍后再试");
            }
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("bookmark import rate limit unavailable, fail open: {}", ex.getMessage());
        }
    }
}
