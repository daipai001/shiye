package com.shiye.user.module.chat.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChatStreamRequest(
        @Pattern(regexp = "\\d+", message = "conversationId 必须是数字") String conversationId,
        @NotNull @Valid Scope scope,
        @NotBlank @Size(max = 4000) String question,
        @Size(max = 64) String model
) {

    public record Scope(
            @NotBlank @Size(max = 16) String type,
            @Pattern(regexp = "\\d+", message = "scope.id 必须是数字") String id
    ) {
    }
}
