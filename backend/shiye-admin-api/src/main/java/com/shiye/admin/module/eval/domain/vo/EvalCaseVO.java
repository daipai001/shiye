package com.shiye.admin.module.eval.domain.vo;

import java.time.LocalDateTime;
import java.util.List;

public record EvalCaseVO(
        String id,
        String userId,
        String userEmail,
        String question,
        List<Long> expectedArticleIds,
        List<Long> expectedChunkIds,
        String goldAnswer,
        List<String> tags,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
