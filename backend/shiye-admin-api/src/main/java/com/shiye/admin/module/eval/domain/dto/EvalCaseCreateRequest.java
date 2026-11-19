package com.shiye.admin.module.eval.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record EvalCaseCreateRequest(
        @NotNull Long userId,
        @NotBlank @Size(max = 4000) String question,
        List<Long> expectedArticleIds,
        List<Long> expectedChunkIds,
        @Size(max = 20000) String goldAnswer,
        List<String> tags,
        String status
) {
}
