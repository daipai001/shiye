package com.shiye.user.module.chat.controller;

import com.shiye.common.api.ErrorCode;
import com.shiye.common.exception.BizException;
import com.shiye.user.common.security.UserContext;
import com.shiye.user.module.chat.domain.dto.ChatStreamRequest;
import com.shiye.user.module.chat.domain.dto.PageChatRequest;
import com.shiye.user.module.chat.service.ChatRateLimitService;
import com.shiye.user.module.chat.service.ChatService;
import com.shiye.user.module.usage.service.UsageService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;
    private final UsageService usageService;
    private final ChatRateLimitService rateLimitService;

    public ChatController(ChatService chatService, UsageService usageService,
                          ChatRateLimitService rateLimitService) {
        this.chatService = chatService;
        this.usageService = usageService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping(value = "/page-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter pageStream(@Valid @RequestBody PageChatRequest request,
                                 @RequestHeader(value = "X-Client", required = false) String client,
                                 HttpServletResponse response) {
        long userId = UserContext.requireUserId();
        applyRateLimit(response, userId);
        usageService.assertCanChat(userId);
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        SseEmitter emitter = new SseEmitter(180_000L);
        chatService.streamPage(userId, client, request, emitter);
        return emitter;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatStreamRequest request,
                             @RequestHeader(value = "X-Client", required = false) String client,
                             HttpServletResponse response) {
        long userId = UserContext.requireUserId();
        applyRateLimit(response, userId);
        usageService.assertCanChat(userId);
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        SseEmitter emitter = new SseEmitter(180_000L);
        chatService.stream(userId, client, request, emitter);
        return emitter;
    }

    private void applyRateLimit(HttpServletResponse response, long userId) {
        ChatRateLimitService.RateLimitResult result = rateLimitService.hit(userId);
        if (result == null) {
            return;
        }
        response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.resetEpochSeconds()));
        if (result.exceeded()) {
            throw new BizException(ErrorCode.RATE_LIMITED, "问答过于频繁，请稍后再试");
        }
    }
}
