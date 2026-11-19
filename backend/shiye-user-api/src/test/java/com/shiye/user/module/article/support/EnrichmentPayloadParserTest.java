package com.shiye.user.module.article.support;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnrichmentPayloadParserTest {

    @Test
    void parsesJsonWithTagsAndRemovesNoise() {
        String raw = """
                ```json
                {"summary":"  介绍 RAG 的切片策略与重叠比例。  ","tags":["RAG","切片","#RAG","资料","", "向量检索"]}
                ```
                """;

        EnrichmentPayload payload = EnrichmentPayloadParser.parse(raw, 5);

        assertEquals("介绍 RAG 的切片策略与重叠比例。", payload.summary());
        assertEquals(List.of("RAG", "切片", "向量检索"), payload.tags());
    }

    @Test
    void limitsTagCount() {
        EnrichmentPayload payload = EnrichmentPayloadParser.parse(
                "{\"summary\":\"摘要内容\",\"tags\":[\"A1\",\"B2\",\"C3\",\"D4\"]}", 2);

        assertEquals(2, payload.tags().size());
        assertEquals(List.of("A1", "B2"), payload.tags());
    }

    @Test
    void fallsBackToPlainTextWhenJsonInvalid() {
        EnrichmentPayload payload = EnrichmentPayloadParser.parse(
                "这是一段没有 JSON 的摘要文本。", 5);

        assertEquals("这是一段没有 JSON 的摘要文本。", payload.summary());
        assertTrue(payload.tags().isEmpty());
    }
}
