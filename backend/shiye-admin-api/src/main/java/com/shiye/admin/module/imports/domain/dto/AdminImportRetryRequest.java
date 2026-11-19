package com.shiye.admin.module.imports.domain.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

public record AdminImportRetryRequest(
        @Size(max = 100) List<Long> itemIds
) {
}
