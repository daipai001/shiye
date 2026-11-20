package com.shiye.user.module.team.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TeamTransferRequest(
        @NotNull
        @Min(1)
        Long userId
) {
}
