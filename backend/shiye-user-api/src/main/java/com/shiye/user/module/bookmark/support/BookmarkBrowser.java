package com.shiye.user.module.bookmark.support;

import com.shiye.common.api.ErrorCode;
import com.shiye.common.exception.BizException;

import java.util.Locale;

public enum BookmarkBrowser {
    CHROME,
    EDGE;

    public static BookmarkBrowser parse(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "browser 不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "browser 仅支持 CHROME / EDGE");
        }
    }
}
