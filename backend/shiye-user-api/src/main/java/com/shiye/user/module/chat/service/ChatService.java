package com.shiye.user.module.chat.service;

import com.shiye.common.api.PageResult;
import com.shiye.user.module.chat.domain.dto.ChatStreamRequest;
import com.shiye.user.module.chat.domain.vo.ConversationDetailVO;
import com.shiye.user.module.chat.domain.vo.ConversationVO;
import com.shiye.user.module.chat.domain.vo.MessageVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ChatService {

    void stream(long userId, String source, ChatStreamRequest request, SseEmitter emitter);

    PageResult<ConversationVO> conversations(long userId, int page, int pageSize);

    ConversationDetailVO conversation(long userId, long conversationId);

    PageResult<MessageVO> messages(long userId, long conversationId,
                                   int page, int pageSize, String order);

    ConversationVO rename(long userId, long conversationId, String title);

    void delete(long userId, long conversationId);
}
