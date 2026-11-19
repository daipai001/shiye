package com.shiye.user.module.eval.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiye.user.module.chat.support.RetrievedChunk;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 评测指标计算：引用准确率取“回答引用来源中命中期望来源的比例”，
 * 答案正确率解析 LLM-as-judge 的 JSON 结果。
 */
public final class EvalMetrics {

    private static final Pattern CITATION = Pattern.compile("\\[(\\d{1,2})]");
    private static final Pattern SCORE = Pattern.compile("(?i)\"?score\"?\\s*:\\s*([0-9.]+)");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private EvalMetrics() {
    }

    public static BigDecimal citationAccuracy(String answer, java.util.List<RetrievedChunk> chunks,
                                              Set<Long> expectedChunks,
                                              Set<Long> expectedArticles) {
        if (answer == null || answer.isBlank() || chunks == null || chunks.isEmpty()) {
            return null;
        }
        Set<Long> expected = !expectedChunks.isEmpty() ? expectedChunks : expectedArticles;
        if (expected.isEmpty()) {
            return null;
        }
        boolean byChunk = !expectedChunks.isEmpty();
        Set<Long> cited = new LinkedHashSet<>();
        Matcher matcher = CITATION.matcher(answer);
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1)) - 1;
            if (index < 0 || index >= chunks.size()) {
                continue;
            }
            RetrievedChunk chunk = chunks.get(index);
            cited.add(byChunk ? chunk.chunkId() : chunk.articleId());
        }
        if (cited.isEmpty()) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        int citedSources = cited.size();
        cited.retainAll(expected);
        return BigDecimal.valueOf(cited.size())
                .divide(BigDecimal.valueOf(citedSources), 4, RoundingMode.HALF_UP);
    }

    public static JudgeResult parseJudge(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(content);
            BigDecimal score = root.path("score").isNumber()
                    ? root.path("score").decimalValue()
                    : new BigDecimal(root.path("score").asText());
            return new JudgeResult(clamp(score), root.path("reason").asText(null));
        } catch (Exception ignored) {
            Matcher matcher = SCORE.matcher(content);
            if (matcher.find()) {
                try {
                    return new JudgeResult(clamp(new BigDecimal(matcher.group(1))), content);
                } catch (NumberFormatException ex) {
                    return null;
                }
            }
            return null;
        }
    }

    private static BigDecimal clamp(BigDecimal score) {
        if (score.signum() < 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        if (score.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);
        }
        return score.setScale(4, RoundingMode.HALF_UP);
    }

    public record JudgeResult(BigDecimal score, String reason) {
    }
}
