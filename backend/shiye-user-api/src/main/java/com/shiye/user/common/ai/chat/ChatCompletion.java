package com.shiye.user.common.ai.chat;

/**
 * 非流式 Chat 完成结果，供摘要/标签等后台任务使用。
 */
public record ChatCompletion(
        String content,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        boolean estimated,
        String finishReason
) {
}
