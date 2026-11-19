package com.shiye.admin.module.eval.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EvalRunCreateRequest(
        @NotNull Long userId,
        @NotBlank @Size(max = 128) String name,
        @Min(1) @Max(20) Integer topK,
        Boolean rerank,
        @Size(max = 64) String model
) {
}
