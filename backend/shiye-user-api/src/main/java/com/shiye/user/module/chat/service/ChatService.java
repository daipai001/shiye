package com.shiye.user.module.chat.service;

import com.shiye.common.api.PageResult;
import com.shiye.user.module.chat.domain.dto.ChatStreamRequest;
import com.shiye.user.module.chat.domain.dto.MessageFeedbackRequest;
import com.shiye.user.module.chat.domain.dto.PageChatRequest;
import com.shiye.user.module.chat.domain.vo.ChatCancelVO;
import com.shiye.user.module.chat.domain.vo.ConversationDetailVO;
import com.shiye.user.module.chat.domain.vo.ConversationVO;
import com.shiye.user.module.chat.domain.vo.MessageVO;
import com.shiye.user.module.usage.domain.vo.ChatReservation;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ChatService {

    void stream(long userId, String source, ChatStreamRequest request, SseEmitter emitter,
                ChatReservation reservation);

    void streamPage(long userId, String source, PageChatRequest request, SseEmitter emitter,
                    ChatReservation reservation);

    /**
     * 解析本次请求最终生效的团队范围（含已有会话的 scope）。
     * 用于在开始 SSE 前做团队配额检查，避免用请求 scope 绕过已有团队会话的配额。
     */
    Long resolveTeamId(long userId, ChatStreamRequest request);

    PageResult<ConversationVO> conversations(long userId, int page, int pageSize);

    ConversationDetailVO conversation(long userId, long conversationId);

    PageResult<MessageVO> messages(long userId, long conversationId,
                                   int page, int pageSize, String order);

    ConversationVO rename(long userId, long conversationId, String title);

    void delete(long userId, long conversationId);

    ChatCancelVO cancel(long userId, String requestId);

    void feedback(long userId, long messageId, MessageFeedbackRequest request);
}
