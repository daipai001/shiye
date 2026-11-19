package com.shiye.admin.module.models.domain.vo;

public record ModelTestVO(
        boolean ok,
        int latencyMs,
        String model,
        String message
) {
}
