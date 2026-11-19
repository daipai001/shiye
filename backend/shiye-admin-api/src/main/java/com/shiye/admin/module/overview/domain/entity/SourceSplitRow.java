package com.shiye.admin.module.overview.domain.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SourceSplitRow {
    private String source;
    private Long calls;
    private Long tokens;
    private BigDecimal cost;
}
