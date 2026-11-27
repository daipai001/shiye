package com.shiye.user.common.ai.vector;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

        List<VectorSearchHit> hits = new RediSearchVectorSearcher(
                null, null, new SimpleMeterRegistry()).parse(reply);

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

    @Test
    void buildsPersonalTeamAndArticleFilteredQueries() {
        String personal = RediSearchVectorSearcher.buildQuery(
                5L, null, null, "BAAI/bge-m3", 30);
        assertTrue(personal.contains("@user_id:{5}"));
        assertTrue(personal.contains("@embedding_model:{baai\\/bge\\-m3}"));
        assertTrue(personal.contains("[KNN 30"));
        assertFalse(personal.contains("@team_id:"));

        String team = RediSearchVectorSearcher.buildQuery(
                5L, 7L, null, "BAAI/bge-m3", 30);
        assertTrue(team.contains("@team_id:{7}"));
        assertFalse(team.contains("@user_id:"));

        String filtered = RediSearchVectorSearcher.buildQuery(
                5L, 7L, new LinkedHashSet<>(List.of(11L, 12L)), "model", 30);
        assertTrue(filtered.contains("@team_id:{7}"));
        assertTrue(filtered.contains("@article_id:{11|12}"));
    }

    @Test
    void validatesIndexFieldsAndDimension() {
        List<Object> attributes = List.of(
                List.of(bytes("identifier"), bytes("user_id"), bytes("type"), bytes("TAG")),
                List.of(bytes("identifier"), bytes("team_id"), bytes("type"), bytes("TAG")),
                List.of(bytes("identifier"), bytes("article_id"), bytes("type"), bytes("TAG")),
                List.of(bytes("identifier"), bytes("chunk_id"), bytes("type"), bytes("NUMERIC")),
                List.of(bytes("identifier"), bytes("embedding_model"), bytes("type"), bytes("TAG")),
                List.of(bytes("identifier"), bytes("vector_bin"), bytes("type"), bytes("VECTOR"),
                        bytes("dim"), bytes("1024")));
        Object reply = List.of(bytes("attributes"), attributes);

        RediSearchVectorIndex.IndexInfo info = RediSearchVectorIndex.parseIndexInfo(reply);

        assertEquals(1024, info.dimension());
        assertTrue(info.valid(1024));
        assertFalse(info.valid(768));
    }

    @Test
    void rejectsIndexWithoutTeamField() {
        List<Object> attributes = List.of(
                List.of(bytes("identifier"), bytes("user_id"), bytes("type"), bytes("TAG")),
                List.of(bytes("identifier"), bytes("article_id"), bytes("type"), bytes("TAG")),
                List.of(bytes("identifier"), bytes("chunk_id"), bytes("type"), bytes("NUMERIC")),
                List.of(bytes("identifier"), bytes("embedding_model"), bytes("type"), bytes("TAG")),
                List.of(bytes("identifier"), bytes("vector_bin"), bytes("type"), bytes("VECTOR"),
                        bytes("dim"), bytes("1024")));
        Object reply = List.of(bytes("attributes"), attributes);

        RediSearchVectorIndex.IndexInfo info = RediSearchVectorIndex.parseIndexInfo(reply);

        assertFalse(info.valid(1024));
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
