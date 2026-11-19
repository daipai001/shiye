package com.shiye.user.module.chat.controller;

import com.shiye.common.api.Result;
import com.shiye.user.common.security.UserContext;
import com.shiye.user.module.chat.domain.dto.MessageFeedbackRequest;
import com.shiye.user.module.chat.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {

    private final ChatService chatService;

    public MessageController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/{id}/feedback")
    public Result<Void> feedback(@PathVariable long id,
                                 @Valid @RequestBody MessageFeedbackRequest request) {
        chatService.feedback(UserContext.requireUserId(), id, request);
        return Result.ok();
    }
}
