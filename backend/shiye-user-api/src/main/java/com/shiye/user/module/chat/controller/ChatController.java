package com.shiye.user.module.chat.controller;

import com.shiye.user.common.security.UserContext;
import com.shiye.user.module.chat.domain.dto.ChatStreamRequest;
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

    public ChatController(ChatService chatService, UsageService usageService) {
        this.chatService = chatService;
        this.usageService = usageService;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatStreamRequest request,
                             @RequestHeader(value = "X-Client", required = false) String client,
                             HttpServletResponse response) {
        long userId = UserContext.requireUserId();
        usageService.assertCanChat(userId);
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        SseEmitter emitter = new SseEmitter(180_000L);
        chatService.stream(userId, client, request, emitter);
        return emitter;
    }
}
