package com.shiye.user.common.ai.chat;

public record ChatCompletionResult(
        int promptTokens,
        int completionTokens,
        int totalTokens,
        boolean estimated,
        String finishReason
) {
}
