package com.shiye.user.module.eval.support;

import com.shiye.user.module.chat.support.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EvalMetricsTest {

    private final List<RetrievedChunk> chunks = List.of(
            new RetrievedChunk(10, 100, "A", "https://a", "内容 A", 0.9, 0, 10),
            new RetrievedChunk(20, 200, "B", "https://b", "内容 B", 0.8, 0, 10)
    );

    @Test
    void citationAccuracyUsesUniqueCitedSources() {
        assertEquals(new BigDecimal("0.5000"), EvalMetrics.citationAccuracy(
                "结论 [1][2]", chunks, Set.of(10L), Set.of()));
    }

    @Test
    void citationAccuracySupportsArticleExpectation() {
        assertEquals(new BigDecimal("1.0000"), EvalMetrics.citationAccuracy(
                "结论 [2]", chunks, Set.of(), Set.of(200L)));
    }

    @Test
    void citationAccuracyIsZeroWhenNoMarker() {
        assertEquals(new BigDecimal("0.0000"), EvalMetrics.citationAccuracy(
                "没有引用", chunks, Set.of(10L), Set.of()));
    }

    @Test
    void citationAccuracyIsNullWithoutExpectedSources() {
        assertNull(EvalMetrics.citationAccuracy(
                "结论 [1]", chunks, Set.of(), Set.of()));
    }

    @Test
    void parsesJudgeJson() {
        EvalMetrics.JudgeResult result = EvalMetrics.parseJudge(
                "{\"score\":0.85,\"reason\":\"覆盖了关键点\"}");

        assertEquals(new BigDecimal("0.8500"), result.score());
        assertEquals("覆盖了关键点", result.reason());
    }

    @Test
    void parsesJudgeFallbackAndClamps() {
        assertEquals(new BigDecimal("1.0000"),
                EvalMetrics.parseJudge("score: 1.7").score());
        assertEquals(new BigDecimal("0.7500"),
                EvalMetrics.parseJudge("{\"score\": 0.75}").score());
    }
}
