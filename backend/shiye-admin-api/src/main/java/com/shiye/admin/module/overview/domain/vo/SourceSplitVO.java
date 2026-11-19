package com.shiye.admin.module.overview.domain.vo;

import java.math.BigDecimal;

public record SourceSplitVO(
        String source,
        long calls,
        long tokens,
        BigDecimal cost
) {
}
