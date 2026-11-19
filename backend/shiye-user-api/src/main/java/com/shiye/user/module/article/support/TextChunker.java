package com.shiye.user.module.article.support;

import com.shiye.common.security.Hashes;
import com.shiye.common.settings.SystemSettings;
import com.shiye.user.common.settings.RuntimeSystemSettingsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 按段落/句子边界切片，单块 500~800 中文字，重叠约 100 字。
 * charStart/charEnd 直接对应 articles.content_text，用于引用定位。
 */
@Component
public class TextChunker {

    private final RuntimeSystemSettingsService systemSettingsService;
    private final int maxSize;
    private final int minSize;
    private final int maxChunks;

    public TextChunker(
            RuntimeSystemSettingsService systemSettingsService,
            @Value("${shiye.article.chunk-max-size:900}") int maxSize,
            @Value("${shiye.article.chunk-min-size:200}") int minSize,
            @Value("${shiye.article.max-chunks:500}") int maxChunks) {
        this.systemSettingsService = systemSettingsService;
        this.maxSize = Math.max(300, maxSize);
        this.minSize = Math.max(100, Math.min(minSize, this.maxSize));
        this.maxChunks = Math.max(1, maxChunks);
    }

    public List<TextChunk> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        SystemSettings settings = systemSettingsService.current();
        int targetSize = Math.max(minSize, Math.min(settings.ragChunkTargetSize(), maxSize));
        int overlap = Math.max(0, Math.min(settings.ragChunkOverlap(), maxSize / 3));
        List<TextChunk> chunks = new ArrayList<>();
        int length = text.length();
        int start = 0;
        int index = 0;

        while (start < length) {
            if (chunks.size() >= maxChunks) {
                throw new IngestException("TOO_MANY_CHUNKS",
                        "切片数量超过上限 " + maxChunks);
            }

            int hardEnd = Math.min(length, start + maxSize);
            int end;
            if (length - start <= maxSize) {
                end = length;
            } else {
                int center = start + targetSize;
                int lower = Math.max(start + minSize, center - targetSize / 2);
                int upper = Math.min(hardEnd, center + targetSize / 2);
                int near = findBreakNear(text, center, lower, upper);
                end = near > start ? near : findBreakBefore(text, center, hardEnd);
            }

            int contentStart = start;
            while (contentStart < end && Character.isWhitespace(text.charAt(contentStart))) {
                contentStart++;
            }
            int contentEnd = end;
            while (contentEnd > contentStart && Character.isWhitespace(text.charAt(contentEnd - 1))) {
                contentEnd--;
            }
            if (contentEnd > contentStart) {
                String content = text.substring(contentStart, contentEnd);
                chunks.add(new TextChunk(
                        index++,
                        content,
                        contentStart,
                        contentEnd,
                        estimateTokens(content),
                        Hashes.sha256Hex(content)
                ));
            }

            if (end >= length) {
                break;
            }
            int nextStart = Math.max(start + 1, end - overlap);
            nextStart = alignStart(text, nextStart, end);
            if (nextStart <= start) {
                nextStart = end;
            }
            start = nextStart;
        }

        return chunks;
    }

    private int findBreakNear(String text, int center, int lower, int upper) {
        int from = Math.max(lower, Math.min(center, upper - 1));
        for (int i = from; i < upper; i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                return i + 1;
            }
            if (isSentenceEnd(c)) {
                int end = i + 1;
                while (end < text.length() && isClosingQuote(text.charAt(end))) {
                    end++;
                }
                return end;
            }
            if (c == '，' || c == ',' || c == '、' || c == ' ') {
                return i + 1;
            }
        }
        int backwardEnd = Math.min(center - 1, upper - 1);
        for (int i = backwardEnd; i >= lower; i--) {
            char c = text.charAt(i);
            if (c == '\n') {
                return i + 1;
            }
            if (isSentenceEnd(c)) {
                int end = i + 1;
                while (end < text.length() && isClosingQuote(text.charAt(end))) {
                    end++;
                }
                return end;
            }
            if (c == '，' || c == ',' || c == '、' || c == ' ') {
                return i + 1;
            }
        }
        return -1;
    }

    private int findBreakBefore(String text, int min, int max) {
        int lower = Math.max(1, Math.min(min, max - 1));
        for (int i = max - 1; i >= lower; i--) {
            if (text.charAt(i) == '\n') {
                return i + 1;
            }
        }
        for (int i = max - 1; i >= lower; i--) {
            if (isSentenceEnd(text.charAt(i))) {
                int end = i + 1;
                while (end < text.length() && isClosingQuote(text.charAt(end))) {
                    end++;
                }
                return end;
            }
        }
        for (int i = max - 1; i >= lower; i--) {
            char c = text.charAt(i);
            if (c == '，' || c == ',' || c == '、' || c == ' ') {
                return i + 1;
            }
        }
        return max;
    }

    private int alignStart(String text, int desired, int limit) {
        int position = Math.max(1, desired);
        while (position < limit) {
            char previous = text.charAt(position - 1);
            if (Character.isWhitespace(previous) || isSentenceEnd(previous)) {
                return position;
            }
            position++;
        }
        return limit;
    }

    private boolean isSentenceEnd(char value) {
        return value == '。' || value == '！' || value == '？' || value == '；'
                || value == '.' || value == '!' || value == '?' || value == ';';
    }

    private boolean isClosingQuote(char value) {
        return value == '"' || value == '\'' || value == '”' || value == '’'
                || value == '）' || value == ')' || value == '】' || value == ']';
    }

    private int estimateTokens(String content) {
        int cjk = 0;
        int latinWords = 0;
        int other = 0;
        boolean inLatinWord = false;
        for (int i = 0; i < content.length(); ) {
            int codePoint = content.codePointAt(i);
            if (isCjk(codePoint)) {
                cjk++;
                inLatinWord = false;
            } else if (Character.isLetterOrDigit(codePoint)) {
                if (!inLatinWord) {
                    latinWords++;
                    inLatinWord = true;
                }
            } else {
                if (!Character.isWhitespace(codePoint)) {
                    other++;
                }
                inLatinWord = false;
            }
            i += Character.charCount(codePoint);
        }
        int tokens = cjk + (int) Math.ceil(latinWords * 1.3) + (int) Math.ceil(other * 0.3);
        return Math.max(1, tokens);
    }

    private boolean isCjk(int codePoint) {
        return (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
                || (codePoint >= 0x3400 && codePoint <= 0x4DBF)
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF);
    }
}
