package com.shiye.user.module.article.service;

import com.shiye.common.api.ErrorCode;
import com.shiye.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * 资料上传限流：10 次/分钟/用户。Redis 不可用时降级放行。
 */
@Service
public class ArticleUploadRateLimitService {

    private static final Logger log = LoggerFactory.getLogger(ArticleUploadRateLimitService.class);
    private static final int LIMIT_PER_MINUTE = 10;

    private final StringRedisTemplate redisTemplate;

    public ArticleUploadRateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void check(long userId) {
        long minute = Instant.now().getEpochSecond() / 60;
        String key = "shiye:rl:article-upload:" + userId + ":" + minute;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, Duration.ofMinutes(2));
            if (count != null && count > LIMIT_PER_MINUTE) {
                throw new BizException(ErrorCode.RATE_LIMITED, "上传过于频繁，请稍后再试");
            }
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("article upload rate limit unavailable, fail open: {}", ex.getMessage());
        }
    }
}
