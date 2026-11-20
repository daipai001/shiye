package com.shiye.user.module.team.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeamJoinRequest(
        @NotBlank
        @Size(min = 6, max = 32)
        String code
) {
}
