package com.shiye.admin.module.usage.domain.vo;

import java.util.List;

public record AdminUsageResponse(
        List<AdminUsageRecordVO> items,
        long total,
        int page,
        int pageSize,
        AdminUsageSummaryVO summary
) {
}
