package com.shiye.user.module.team.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeamCreateRequest(
        @NotBlank
        @Size(max = 64)
        String name
) {
}
