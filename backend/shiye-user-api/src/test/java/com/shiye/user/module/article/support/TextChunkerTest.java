package com.shiye.user.module.article.support;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextChunkerTest {

    private final TextChunker chunker = new TextChunker(200, 300, 100, 30, 100);

    @Test
    void keepsExactOffsetsAndOverlap() {
        String text = "这是第一句用于测试切片边界。".repeat(40);
        List<TextChunk> chunks = chunker.chunk(text);

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.size() > 1);
        for (TextChunk chunk : chunks) {
            assertEquals(chunk.content(), text.substring(chunk.charStart(), chunk.charEnd()));
            assertTrue(chunk.content().length() <= 300);
            assertTrue(chunk.tokenCount() > 0);
        }
        for (int i = 1; i < chunks.size(); i++) {
            assertTrue(chunks.get(i).charStart() < chunks.get(i - 1).charEnd(),
                    "chunks should overlap");
        }
    }

    @Test
    void keepsShortTextInSingleChunk() {
        String text = "很短的一段正文。";
        List<TextChunk> chunks = chunker.chunk(text);

        assertEquals(1, chunks.size());
        assertEquals(text, chunks.get(0).content());
    }

    @Test
    void returnsEmptyForBlankText() {
        assertTrue(chunker.chunk("   ").isEmpty());
    }
}
