package com.shiye.admin.module.overview.domain.vo;

import com.shiye.admin.module.overview.domain.entity.RecentFailureRow;

import java.time.LocalDateTime;

public record FailureVO(
        String type,
        String resourceId,
        String userName,
        String errorCode,
        String errorMessage,
        LocalDateTime createdAt
) {

    public static FailureVO from(RecentFailureRow row) {
        return new FailureVO(
                row.getType(),
                row.getResourceId(),
                row.getUserName(),
                row.getErrorCode(),
                row.getErrorMessage(),
                row.getCreatedAt()
        );
    }
}
