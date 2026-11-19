package com.shiye.admin.module.eval.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record EvalResultVO(
        String id,
        String caseId,
        String question,
        List<Long> retrievedChunkIds,
        BigDecimal recallAt5,
        BigDecimal citationAccuracy,
        BigDecimal answerCorrectness,
        Integer latencyMs,
        BigDecimal cost,
        LocalDateTime createdAt
) {
}
