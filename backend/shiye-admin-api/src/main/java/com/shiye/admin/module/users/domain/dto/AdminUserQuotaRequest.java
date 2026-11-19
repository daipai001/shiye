package com.shiye.admin.module.users.domain.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AdminUserQuotaRequest(
        @Min(value = 1000, message = "monthlyTokenQuota 至少为 1000")
        long monthlyTokenQuota,
        @NotNull @DecimalMin(value = "0", message = "monthlyCostQuota 不能为负")
        BigDecimal monthlyCostQuota
) {
}
