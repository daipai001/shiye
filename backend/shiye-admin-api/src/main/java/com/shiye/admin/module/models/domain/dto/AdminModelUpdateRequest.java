package com.shiye.admin.module.models.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AdminModelUpdateRequest(
        @NotNull @Valid ChatUpdate chat,
        @NotNull @Valid EmbeddingUpdate embedding,
        @NotNull @Valid RerankUpdate rerank
) {

    public record ChatUpdate(
            @NotBlank @Size(max = 512)
            @Pattern(regexp = "^https?://.+", message = "chat.baseUrl 必须是 http/https")
            String baseUrl,
            @NotBlank @Size(max = 128) String model,
            @Size(max = 512) String apiKey,
            @Min(64) @Max(8192) int maxTokens,
            @DecimalMin("0.0") @DecimalMax("2.0") double temperature,
            @NotNull @DecimalMin("0.0") BigDecimal inputPrice,
            @NotNull @DecimalMin("0.0") BigDecimal outputPrice
    ) {
    }

    public record EmbeddingUpdate(
            @NotBlank @Size(max = 512)
            @Pattern(regexp = "^https?://.+", message = "embedding.baseUrl 必须是 http/https")
            String baseUrl,
            @NotBlank @Size(max = 128) String model,
            @Size(max = 512) String apiKey,
            @Min(8) @Max(4096) int dimension,
            @Min(1) @Max(64) int batchSize
    ) {
    }

    public record RerankUpdate(
            boolean enabled,
            @Size(max = 512) String baseUrl,
            @Size(max = 128) String model,
            @Size(max = 512) String apiKey
    ) {
    }
}
