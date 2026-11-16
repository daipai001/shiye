package com.shiye.user.module.article.support;

import com.shiye.common.api.ErrorCode;
import com.shiye.common.exception.BizException;

import java.util.Locale;

public enum ArticleStatus {
    PENDING,
    PARSING,
    CHUNKING,
    EMBEDDING,
    READY,
    FAILED;

    public static ArticleStatus parse(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "status 不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "status 取值非法");
        }
    }
}
