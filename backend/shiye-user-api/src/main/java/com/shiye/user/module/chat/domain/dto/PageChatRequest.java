package com.shiye.user.module.chat.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 当前页面问答：正文只作为本次请求的临时上下文，不写入会话/资料库。
 */
public record PageChatRequest(
        @Size(max = 2048) String url,
        @Size(max = 512) String title,
        @NotBlank @Size(max = 200000) String content,
        @NotBlank @Size(max = 4000) String question
) {
}
