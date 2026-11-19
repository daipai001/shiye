package com.shiye.user.module.article.service;

import com.shiye.common.api.ErrorCode;
import com.shiye.common.exception.BizException;
import com.shiye.user.common.ratelimit.MinuteRateLimiter;
import org.springframework.stereotype.Service;

/**
 * 资料上传限流：10 次/分钟/用户。
 */
@Service
public class ArticleUploadRateLimitService {

    private static final int LIMIT_PER_MINUTE = 10;

    private final MinuteRateLimiter rateLimiter;

    public ArticleUploadRateLimitService(MinuteRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    public void check(long userId) {
        MinuteRateLimiter.RateLimitResult result = rateLimiter.hit(
                "article-upload", String.valueOf(userId), LIMIT_PER_MINUTE);
        if (result != null && result.exceeded()) {
            throw new BizException(ErrorCode.RATE_LIMITED, "上传过于频繁，请稍后再试");
        }
    }
}
