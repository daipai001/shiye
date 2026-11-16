package com.shiye.user.module.chat.domain.vo;

public record ChatSseError(String code, String message, boolean retryable) {
}
