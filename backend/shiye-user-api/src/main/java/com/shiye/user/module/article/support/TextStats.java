package com.shiye.user.module.article.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextStats {

    private static final Pattern LATIN_WORD = Pattern.compile("[A-Za-z0-9]+");

    private TextStats() {
    }

    public static String summary(String content, int maxLength) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String trimmed = content.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength) + "…";
    }

    public static String language(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        int cjk = 0;
        int latin = 0;
        for (int i = 0; i < content.length(); ) {
            int codePoint = content.codePointAt(i);
            if (isCjk(codePoint)) {
                cjk++;
            } else if (Character.isLetter(codePoint)) {
                latin++;
            }
            i += Character.charCount(codePoint);
        }
        if (cjk == 0 && latin == 0) {
            return null;
        }
        return cjk >= 20 && cjk >= latin * 0.15 ? "zh" : "en";
    }

    public static int wordCount(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        int cjk = 0;
        for (int i = 0; i < content.length(); ) {
            int codePoint = content.codePointAt(i);
            if (isCjk(codePoint)) {
                cjk++;
            }
            i += Character.charCount(codePoint);
        }
        int latinWords = 0;
        Matcher matcher = LATIN_WORD.matcher(content);
        while (matcher.find()) {
            latinWords++;
        }
        long total = (long) cjk + latinWords;
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public static boolean isCjk(int codePoint) {
        return (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
                || (codePoint >= 0x3400 && codePoint <= 0x4DBF)
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF);
    }
}
