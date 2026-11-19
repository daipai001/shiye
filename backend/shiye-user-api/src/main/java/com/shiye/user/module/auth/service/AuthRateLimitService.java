package com.shiye.user.module.auth.service;

import com.shiye.user.common.ratelimit.MinuteRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 登录/注册 IP 限流：默认 10 次/分钟/IP。
 */
@Service
public class AuthRateLimitService {

    private final MinuteRateLimiter rateLimiter;
    private final int limitPerMinute;

    public AuthRateLimitService(MinuteRateLimiter rateLimiter,
                                @Value("${shiye.auth.rate-limit-per-minute:10}") int limitPerMinute) {
        this.rateLimiter = rateLimiter;
        this.limitPerMinute = Math.max(1, limitPerMinute);
    }

    public MinuteRateLimiter.RateLimitResult hit(String ip) {
        return rateLimiter.hit("auth", ip, limitPerMinute);
    }
}
