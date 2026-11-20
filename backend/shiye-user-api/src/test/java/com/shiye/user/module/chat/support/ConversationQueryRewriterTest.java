package com.shiye.user.module.chat.support;

import com.shiye.user.module.chat.domain.entity.MessageEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationQueryRewriterTest {

    @Test
    void keepsStandaloneQuestion() {
        String question = "请说明 RAG 的 chunk size 一般建议设置多大，以及重叠比例是多少？";
        String rewritten = ConversationQueryRewriter.rewrite(
                question, List.of(user("我之前问过别的")));

        assertEquals(question, rewritten);
    }

    @Test
    void expandsContextDependentQuestion() {
        String rewritten = ConversationQueryRewriter.rewrite(
                "这个怎么配置？",
                List.of(user("BGE-reranker 怎么接入？"), assistant("可以配置 rerank key。")));

        assertTrue(rewritten.contains("BGE-reranker 怎么接入？"));
        assertTrue(rewritten.contains("这个怎么配置？"));
    }

    @Test
    void ignoresAssistantMessages() {
        String rewritten = ConversationQueryRewriter.rewrite(
                "继续", List.of(assistant("上一条回答")));

        assertEquals("继续", rewritten);
        assertFalse(rewritten.contains("上一条回答"));
    }

    private MessageEntity user(String content) {
        MessageEntity message = new MessageEntity();
        message.setRole("USER");
        message.setContent(content);
        return message;
    }

    private MessageEntity assistant(String content) {
        MessageEntity message = new MessageEntity();
        message.setRole("ASSISTANT");
        message.setContent(content);
        return message;
    }
}
