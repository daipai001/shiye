package com.shiye.user.module.article.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 解析摘要/标签模型输出。优先取 JSON；解析失败时把全文当作摘要，避免整次任务白跑。
 */
public final class EnrichmentPayloadParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_SUMMARY_CHARS = 1000;
    private static final int MAX_TAG_NAME_CHARS = 32;
    private static final Set<String> GENERIC_TAGS = Set.of(
            "文章", "资料", "内容", "文档", "知识", "信息", "文本"
    );

    private EnrichmentPayloadParser() {
    }

    public static EnrichmentPayload parse(String raw, int maxTags) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return new EnrichmentPayload("", List.of());
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            try {
                JsonNode root = MAPPER.readTree(text.substring(start, end + 1));
                String summary = cleanSummary(root.path("summary").asText(""));
                List<String> tags = cleanTags(root.path("tags"), maxTags);
                if (!summary.isEmpty()) {
                    return new EnrichmentPayload(summary, tags);
                }
            } catch (Exception ignored) {
                // 非 JSON 输出，走纯文本摘要
            }
        }
        return new EnrichmentPayload(cleanSummary(text), List.of());
    }

    private static String cleanSummary(String raw) {
        if (raw == null) {
            return "";
        }
        String summary = raw.trim()
                .replaceAll("(?is)^```(?:json)?\\s*", "")
                .replaceAll("```$", "")
                .trim();
        if (summary.startsWith("\"") && summary.endsWith("\"") && summary.length() > 1) {
            summary = summary.substring(1, summary.length() - 1).trim();
        }
        summary = summary.replaceAll("\\s+", " ");
        if (summary.length() > MAX_SUMMARY_CHARS) {
            summary = summary.substring(0, MAX_SUMMARY_CHARS);
        }
        return summary;
    }

    private static List<String> cleanTags(JsonNode node, int maxTags) {
        int limit = Math.max(0, Math.min(maxTags, 10));
        if (limit == 0 || node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        List<String> rawTags = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item.isObject()) {
                    rawTags.add(item.path("name").asText(""));
                } else {
                    rawTags.add(item.asText(""));
                }
            }
        } else if (node.isTextual()) {
            for (String part : node.asText("").split("[,，、;；\\n]")) {
                rawTags.add(part);
            }
        }

        List<String> tags = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String raw : rawTags) {
            String tag = cleanTag(raw);
            if (tag == null) {
                continue;
            }
            String key = tag.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                continue;
            }
            tags.add(tag);
            if (tags.size() >= limit) {
                break;
            }
        }
        return tags;
    }

    private static String cleanTag(String raw) {
        if (raw == null) {
            return null;
        }
        String tag = raw.trim();
        while (tag.startsWith("#")) {
            tag = tag.substring(1).trim();
        }
        if (tag.startsWith("\"") && tag.endsWith("\"") && tag.length() > 1) {
            tag = tag.substring(1, tag.length() - 1).trim();
        }
        tag = tag.replaceAll("\\s+", " ");
        if (tag.isEmpty() || GENERIC_TAGS.contains(tag)) {
            return null;
        }
        return tag.length() <= MAX_TAG_NAME_CHARS
                ? tag
                : tag.substring(0, MAX_TAG_NAME_CHARS);
    }
}
