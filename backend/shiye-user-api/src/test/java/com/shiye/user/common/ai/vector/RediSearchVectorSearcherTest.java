package com.shiye.user.common.ai.vector;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RediSearchVectorSearcherTest {

    @Test
    void parsesResp3SearchReply() {
        List<Object> attrs = List.of(
                bytes("score"), bytes("0.25"),
                bytes("article_id"), bytes("3"),
                bytes("chunk_id"), bytes("7"),
                bytes("content"), bytes("hello"),
                bytes("char_start"), bytes("10"),
                bytes("char_end"), bytes("20"));
        List<Object> entry = List.of(
                bytes("id"), bytes("kb:chunk:7"),
                bytes("extra_attributes"), attrs);
        Object reply = List.of(bytes("results"), List.of(entry));

        List<VectorSearchHit> hits = new RediSearchVectorSearcher(null, null).parse(reply);

        assertEquals(1, hits.size());
        assertEquals(3, hits.get(0).articleId());
        assertEquals(7, hits.get(0).chunkId());
        assertEquals("hello", hits.get(0).content());
        assertEquals(0.75, hits.get(0).score(), 0.0001);
    }

    @Test
    void escapesTagSpecialCharactersAndLowercases() {
        assertEquals("baai\\/bge\\-m3", RediSearchVectorSearcher.escapeTag("BAAI/bge-m3"));
        assertEquals("plain", RediSearchVectorSearcher.escapeTag("PLAIN"));
        assertTrue(RediSearchVectorSearcher.escapeTag("a b").contains("\\"));
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
