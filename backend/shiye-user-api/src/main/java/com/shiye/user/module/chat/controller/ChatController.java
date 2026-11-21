package com.shiye.user.module.chat.controller;

import com.shiye.common.api.ErrorCode;
import com.shiye.common.api.Result;
import com.shiye.common.exception.BizException;
import com.shiye.user.common.ratelimit.MinuteRateLimiter;
import com.shiye.user.common.security.UserContext;
import com.shiye.user.module.chat.domain.dto.ChatStreamRequest;
import com.shiye.user.module.chat.domain.dto.PageChatRequest;
import com.shiye.user.module.chat.domain.vo.ChatCancelVO;
import com.shiye.user.module.chat.service.ChatRateLimitService;
import com.shiye.user.module.chat.service.ChatService;
import com.shiye.user.module.team.service.TeamTaxonomyService;
import com.shiye.user.module.usage.domain.vo.TeamReservation;
import com.shiye.user.module.usage.service.UsageService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;
    private final UsageService usageService;
    private final ChatRateLimitService rateLimitService;
    private final TeamTaxonomyService teamTaxonomyService;

    public ChatController(ChatService chatService, UsageService usageService,
                          ChatRateLimitService rateLimitService,
                          TeamTaxonomyService teamTaxonomyService) {
        this.chatService = chatService;
        this.usageService = usageService;
        this.rateLimitService = rateLimitService;
        this.teamTaxonomyService = teamTaxonomyService;
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
        Long teamId = teamId(request.scope());
        usageService.assertCanChat(userId, teamId);
        TeamReservation reservation = teamId == null
                ? null
                : usageService.reserveTeamChat(
                        userId, teamId, 12_000, new java.math.BigDecimal("0.12"));
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        SseEmitter emitter = new SseEmitter(180_000L);
        chatService.stream(userId, client, request, emitter, reservation);
        return emitter;
    }

    @PostMapping("/{requestId}/cancel")
    public Result<ChatCancelVO> cancel(@PathVariable String requestId) {
        return Result.ok(chatService.cancel(UserContext.requireUserId(), requestId));
    }

    private Long teamId(ChatStreamRequest.Scope scope) {
        if (scope == null) {
            return null;
        }
        String type = scope.type() == null ? "" : scope.type().toUpperCase();
        if (!Set.of("TEAM", "TEAM_TAG", "TEAM_FOLDER").contains(type)) {
            return null;
        }
        if (scope.id() == null || !scope.id().matches("\\d+")) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, type + " 范围需要 ID");
        }
        long id = Long.parseLong(scope.id());
        if ("TEAM".equals(type)) {
            return id;
        }
        if ("TEAM_TAG".equals(type)) {
            return teamTaxonomyService.teamIdForTag(id);
        }
        return teamTaxonomyService.teamIdForFolder(id);
    }

    private void applyRateLimit(HttpServletResponse response, long userId) {
        MinuteRateLimiter.RateLimitResult result = rateLimitService.hit(userId);
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
