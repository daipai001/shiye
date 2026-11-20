package com.shiye.user.module.team.domain.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TeamUpdateRequest(
        @NotBlank
        @Size(max = 64)
        String name,

        @NotNull
        @Min(0)
        Long monthlyTokenQuota,

        @NotNull
        @DecimalMin("0")
        BigDecimal monthlyCostQuota
) {
}
