package com.shiye.user.module.bookmark.support;

import com.shiye.common.api.ErrorCode;
import com.shiye.common.exception.BizException;

import java.util.Locale;

public enum BookmarkImportStatus {
    PENDING,
    RUNNING,
    PAUSED,
    DONE,
    FAILED,
    CANCELLED;

    public static BookmarkImportStatus parse(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "status 不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "status 取值非法");
        }
    }

    public boolean isActive() {
        return this == PENDING || this == RUNNING || this == PAUSED;
    }
}
