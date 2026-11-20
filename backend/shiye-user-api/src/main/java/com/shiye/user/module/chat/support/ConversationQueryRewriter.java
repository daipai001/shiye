package com.shiye.user.module.chat.support;

import com.shiye.user.module.chat.domain.entity.MessageEntity;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 轻量多轮检索改写：当前问题依赖上下文时，把最近用户问题拼进检索 query。
 */
public final class ConversationQueryRewriter {

    private static final int MAX_QUERY_CHARS = 500;
    private static final Set<String> CONTEXT_MARKERS = Set.of(
            "这个", "那个", "它", "上面", "前面", "刚才", "继续", "还有",
            "为什么", "怎么", "如何", "哪一个", "对比", "详细说说"
    );

    private ConversationQueryRewriter() {
    }

    public static String rewrite(String question, List<MessageEntity> recentDescending) {
        if (question == null || question.isBlank()) {
            return "";
        }
        String current = question.trim();
        if (recentDescending == null || recentDescending.isEmpty()) {
            return truncate(current);
        }
        boolean needsContext = current.length() <= 24
                || CONTEXT_MARKERS.stream().anyMatch(current::contains);
        if (!needsContext) {
            return truncate(current);
        }

        LinkedHashSet<String> previous = new LinkedHashSet<>();
        for (MessageEntity message : recentDescending) {
            if (previous.size() >= 2) {
                break;
            }
            if (!"USER".equals(message.getRole())) {
                continue;
            }
            String content = message.getContent();
            if (content != null && !content.isBlank()) {
                previous.add(content.trim());
            }
        }
        if (previous.isEmpty()) {
            return truncate(current);
        }
        StringBuilder query = new StringBuilder();
        previous.forEach(value -> query.append(value).append(" "));
        query.append(current);
        return truncate(query.toString());
    }

    private static String truncate(String value) {
        return value.length() <= MAX_QUERY_CHARS
                ? value : value.substring(0, MAX_QUERY_CHARS);
    }
}
