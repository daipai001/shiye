package com.shiye.user.module.chat.service;

import com.shiye.user.common.ratelimit.MinuteRateLimiter;
import com.shiye.user.common.settings.RuntimeSystemSettingsService;
import org.springframework.stereotype.Service;

/**
 * 问答限流：默认 10 次/分钟/用户，可在系统设置中调整。
 */
@Service
public class ChatRateLimitService {

    private final MinuteRateLimiter rateLimiter;
    private final RuntimeSystemSettingsService systemSettingsService;

    public ChatRateLimitService(MinuteRateLimiter rateLimiter,
                                RuntimeSystemSettingsService systemSettingsService) {
        this.rateLimiter = rateLimiter;
        this.systemSettingsService = systemSettingsService;
    }

    public MinuteRateLimiter.RateLimitResult hit(long userId) {
        int limit = systemSettingsService.current().chatRateLimitPerMinute();
        return rateLimiter.hit("chat", String.valueOf(userId), limit);
    }
}
