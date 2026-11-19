package com.shiye.admin.module.models.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ModelTestRequest(
        @NotBlank
        @Pattern(regexp = "CHAT|EMBEDDING|RERANK", message = "type 仅支持 CHAT / EMBEDDING / RERANK")
        String type
) {
}
