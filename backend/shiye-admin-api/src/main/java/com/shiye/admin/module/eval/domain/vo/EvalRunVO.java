package com.shiye.admin.module.eval.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;

public record EvalRunVO(
        String id,
        String userId,
        String userName,
        String name,
        String model,
        String embeddingModel,
        Integer topK,
        boolean rerank,
        String status,
        JsonNode metrics,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt,
        List<EvalResultVO> results
) {
}
