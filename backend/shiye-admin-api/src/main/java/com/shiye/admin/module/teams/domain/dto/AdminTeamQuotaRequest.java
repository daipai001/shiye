package com.shiye.admin.module.teams.domain.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AdminTeamQuotaRequest(
        @NotNull
        @Min(0)
        Long monthlyTokenQuota,

        @NotNull
        @DecimalMin("0")
        BigDecimal monthlyCostQuota
) {
}
