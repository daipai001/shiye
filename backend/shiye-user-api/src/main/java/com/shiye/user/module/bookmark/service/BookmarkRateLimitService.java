package com.shiye.user.module.bookmark.service;

import com.shiye.common.api.ErrorCode;
import com.shiye.common.exception.BizException;
import com.shiye.user.common.ratelimit.MinuteRateLimiter;
import org.springframework.stereotype.Service;

/**
 * 书签导入创建限流：5 次/分钟/用户。
 */
@Service
public class BookmarkRateLimitService {

    private static final int LIMIT_PER_MINUTE = 5;

    private final MinuteRateLimiter rateLimiter;

    public BookmarkRateLimitService(MinuteRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    public void checkCreate(long userId) {
        MinuteRateLimiter.RateLimitResult result = rateLimiter.hit(
                "bookmark-import", String.valueOf(userId), LIMIT_PER_MINUTE);
        if (result != null && result.exceeded()) {
            throw new BizException(ErrorCode.RATE_LIMITED, "书签导入过于频繁，请稍后再试");
        }
    }
}
