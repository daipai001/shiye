package com.shiye.user.module.chat.controller;

import com.shiye.common.api.PageResult;
import com.shiye.common.api.Result;
import com.shiye.user.common.security.UserContext;
import com.shiye.user.module.chat.domain.dto.ConversationRenameRequest;
import com.shiye.user.module.chat.domain.vo.ConversationDetailVO;
import com.shiye.user.module.chat.domain.vo.ConversationVO;
import com.shiye.user.module.chat.domain.vo.MessageVO;
import com.shiye.user.module.chat.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    private final ChatService chatService;

    public ConversationController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping
    public Result<PageResult<ConversationVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(chatService.conversations(
                UserContext.requireUserId(), page, pageSize));
    }

    @GetMapping("/{id}")
    public Result<ConversationDetailVO> detail(@PathVariable long id) {
        return Result.ok(chatService.conversation(UserContext.requireUserId(), id));
    }

    @PatchMapping("/{id}")
    public Result<ConversationVO> rename(@PathVariable long id,
                                         @Valid @RequestBody ConversationRenameRequest request) {
        return Result.ok(chatService.rename(
                UserContext.requireUserId(), id, request.title()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable long id) {
        chatService.delete(UserContext.requireUserId(), id);
        return Result.ok();
    }

    @GetMapping("/{id}/messages")
    public Result<PageResult<MessageVO>> messages(
            @PathVariable long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int pageSize,
            @RequestParam(defaultValue = "asc") String order) {
        return Result.ok(chatService.messages(
                UserContext.requireUserId(), id, page, pageSize, order));
    }
}
