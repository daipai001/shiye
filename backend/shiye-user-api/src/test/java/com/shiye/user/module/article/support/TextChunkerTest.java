package com.shiye.user.module.article.support;

import com.shiye.common.settings.SystemSettings;
import com.shiye.user.common.settings.RuntimeSystemSettingsService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TextChunkerTest {

    private final TextChunker chunker = buildChunker();

    private static TextChunker buildChunker() {
        RuntimeSystemSettingsService settingsService = mock(RuntimeSystemSettingsService.class);
        when(settingsService.current()).thenReturn(new SystemSettings(
                true, 200000, new BigDecimal("5.0"), 2000000, new BigDecimal("20.0"),
                0.8, 10, 0, 200, 30, 8, true, true, true, false, null, false, null));
        return new TextChunker(settingsService, 300, 100, 100);
    }

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
